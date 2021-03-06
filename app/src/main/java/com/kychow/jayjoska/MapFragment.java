package com.kychow.jayjoska;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.android.PolyUtil;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.TravelMode;
import com.kychow.jayjoska.helpers.MapsInfoWindowAdapter;
import com.kychow.jayjoska.models.Place;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;



/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 */
public class MapFragment extends Fragment implements ItineraryAdapter.OnItineraryReorderedListener, GoogleApiClient.OnConnectionFailedListener{

    private GoogleMap map;
    private Location mCurrentLocation;
    protected GoogleApiClient mGoogleApiClient;
    private MapView mapView;
    private Bundle savedState;
    private FloatingActionButton mSetLocation;
    private ProgressBar mLoading;
    private final static String KEY_LOCATION = "location";
    private final static String TAG = "MapFragemnt";
    private String mAddress;
    private OnNewAddressListener mOnNewAddressListener;
    private OnMarkerClickedListener mOnMarkerClickedListener;
    private OnLocationUpdateListener mOnLocationUpdated;
    private OnTravelTimeUpdatedListener mOnTravelTimeUpdatedListener;
    private OnItineraryMarkerClicked mOnItineraryMarkerClicked;
    private OnMapListener mapListener;
    private PlaceAutocompleteAdapter mPlaceAutoCompleteAdapter;

    private Button mRecalculate;

    //hacking location
    private Location mLocation;
    private double lat;
    private double lng;

    private static final LatLngBounds LAT_LNG_BOUNDS = new LatLngBounds(new LatLng(-40, -168), new LatLng(71, 136));

    public MapFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        if (savedInstanceState != null && savedInstanceState.keySet().contains(KEY_LOCATION)) {
            // Since KEY_LOCATION was found in the Bundle, we can be sure that mCurrentLocation
            // is not null.
            //mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION);
        }

    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        mGoogleApiClient = new GoogleApiClient
                .Builder(getContext())
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .enableAutoManage(getActivity(), this)
                .build();

        mPlaceAutoCompleteAdapter = new PlaceAutocompleteAdapter(getContext(), mGoogleApiClient, LAT_LNG_BOUNDS, null);

        mapView = view.findViewById(R.id.mvMap);
        mLoading = view.findViewById(R.id.pbLoadingMap);
        mLoading.setVisibility(View.VISIBLE);
        savedState = new Bundle();
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                loadMap(googleMap);
                if (getTag().equalsIgnoreCase("itinerarymap")) {
                    mapListener.sendItinerary();
                    ArrayList<DirectionsResult> dr = getResults(mapListener.getItinerary());
                    addPolyline(dr, map);
                    mOnTravelTimeUpdatedListener.updateTravelTime(getTravelTime(dr));
                }
                mLoading.setVisibility(View.GONE);
            }
        });

        mSetLocation = view.findViewById(R.id.btnSetLocation);

        if (getTag().equals("RecsMap")) {
            mSetLocation.setVisibility(View.VISIBLE);
            mSetLocation.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final AlertDialog builder = new AlertDialog.Builder(getContext()).create();
                    final AutoCompleteTextView input = new AutoCompleteTextView(getContext());
                    input.setAdapter(mPlaceAutoCompleteAdapter);
                    builder.setTitle("Set location");
                    builder.setView(input);
                    builder.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mAddress = input.getText().toString();
                            String formattedAddress = mAddress.replace(" ", "+");
                            try {
                                mOnNewAddressListener.requestRecs(formattedAddress);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (ApiException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    builder.setButton(DialogInterface.BUTTON_NEGATIVE, "CANCEL", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.setOnShowListener(new DialogInterface.OnShowListener() {
                        @Override
                        public void onShow(DialogInterface dialog) {
                            builder.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.textSecondary));
                            builder.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.textSecondary));
                        }
                    });
                    builder.show();
                }
            });
        }

        if (getTag().equals("ItineraryMap")) {
            mSetLocation.setVisibility(View.INVISIBLE);
            mRecalculate = view.findViewById(R.id.btnRecalculate);
            hideRecalculateButton();
            mRecalculate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mRecalculate.setClickable(false);
                    ArrayList<Place> itinerary;
                    if (mapListener != null) {
                        itinerary = mapListener.getItinerary();
                        addMarkers(itinerary);
                        ArrayList<DirectionsResult> dr = getResults(itinerary);
                        addPolyline(dr, map);
                        mOnTravelTimeUpdatedListener.updateTravelTime(getTravelTime(dr));
                        mRecalculate.setClickable(true);
                    }
                    hideRecalculateButton();
                }
            });
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (mLocation == null) {
            if (ActivityCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Criteria criteria = new Criteria();
                LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
                String provider = locationManager.getBestProvider(criteria, false);
                mLocation = locationManager.getLastKnownLocation(provider);
                lat = mLocation.getLatitude();
                lng = mLocation.getLongitude();
            } else {
                mLocation = new Location("");
                lat = 37.484377;
                lng = -122.148304;
                mLocation.setLatitude(lat);
                mLocation.setLongitude(lng);
            }

            Bundle bundle = this.getArguments();

            if (bundle != null) {
                mAddress = bundle.getString("address");
                Log.d("MapFragment", "bundle is not empty: " + mAddress);
                try {
                    updateLocation(mAddress);
                    mOnNewAddressListener.requestRecs(mAddress);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ApiException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                Log.d("MapFragment", "bundle is empty");
            }
        }

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            mapListener = (OnMapListener) context;
            Log.d("MapFragment", "mapListener has been assigned");
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnMapListener");
        }

        try {
            mOnNewAddressListener = (OnNewAddressListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnNewAddressListener");
        }

        try {
            mOnMarkerClickedListener = (OnMarkerClickedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnMarkerClickedListener");
        }

        try {
            mOnLocationUpdated = (OnLocationUpdateListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnLocationUpdatedListener");
        }


        try {
            mOnTravelTimeUpdatedListener = (OnTravelTimeUpdatedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnTravelTimeUpdatedListener");
        }

        try {
            mOnItineraryMarkerClicked = (OnItineraryMarkerClicked) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnItineraryMarkerClicked");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mapListener = null;
        mOnNewAddressListener = null;
        mOnMarkerClickedListener = null;
        mOnLocationUpdated = null;
        mOnTravelTimeUpdatedListener = null;
        mRecalculate = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mCurrentLocation != null) {
            Log.i(TAG, "GPS location was found");
            LatLng latLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
            map.animateCamera(cameraUpdate);
        } else {
            Log.d(TAG, "Current location was null, enable GPS on emulator");
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        super.onPause();
        mGoogleApiClient.stopAutoManage(getActivity());
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    protected void loadMap(GoogleMap googleMap) {
        map = googleMap;
        if (map != null) {
            // Map is ready
            Log.i(TAG, "Map was loaded properly");
        } else {
            Log.d(TAG, "Error - Map was null!");
        }
    }

    public void addMarkers(ArrayList<Place> places) {
        map.setInfoWindowAdapter(new MapsInfoWindowAdapter(getLayoutInflater()));
        Marker marker;
        if (map != null) {
            map.clear();
            if (!places.isEmpty()) {
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                marker = map.addMarker(new MarkerOptions()
                .position(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()))
                        .title("Current Location")
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.pegman))
                );
                marker.setZIndex(places.size());
                builder.include(marker.getPosition());
                for (Place place : places) {
                    if (place.getPrice().equals("")) {
                        place.setPrice("No Price Range");
                    }
                    marker = map.addMarker(new MarkerOptions()
                            .position(new LatLng(place.getLatitude(), place.getLongitude()))
                            .title(place.getName())
                            .snippet(String.format("Distance: %.2f miles \nPrice: %s", place.getDistance(), place.getPrice()))
                            .icon(getMarkerIcon(Categories.getColorPins().get(place.getCategory()))));
                    builder.include(marker.getPosition());
                }
                LatLngBounds bounds = builder.build();
                // TODO: change padding to be 100 at the top, and a lot less on the sides
                int padding = 100; // offset from edges of the map in pixels
                CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                map.moveCamera(cu);
                map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        if (getTag().equals("RecsMap")) {
                            mOnMarkerClickedListener.scrollToItem(marker.getTitle());
                        } else if (getTag().equals("ItineraryMap")) {
                            mOnItineraryMarkerClicked.scrollItinerary(marker.getTitle());
                        }
                        marker.showInfoWindow();
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    public interface OnMapListener {
        void sendItinerary();
        ArrayList<Place> getItinerary();
    }

    public interface OnNewAddressListener {
        void requestRecs(String s) throws InterruptedException, ApiException, IOException;
    }

    public interface OnMarkerClickedListener {
        void scrollToItem(String s);
    }

    private GeoApiContext getGeoContext() {
        GeoApiContext geoApiContext = new GeoApiContext.Builder()
                .apiKey(getString(R.string.maps_api_key))
                .build();
        return geoApiContext;
    }

    private ArrayList<DirectionsResult> getResults(ArrayList<Place> itinerary) {
        ArrayList<com.google.maps.model.LatLng> destinationCoordinates = new ArrayList<>();
        ArrayList<DirectionsResult> directionsResults = new ArrayList<>();
        DirectionsResult result;

        destinationCoordinates.add(new com.google.maps.model.LatLng(mLocation.getLatitude(), mLocation.getLongitude()));

        for (Place place : itinerary) {
            destinationCoordinates.add(new com.google.maps.model.LatLng(place.getLatitude(), place.getLongitude()));
        }

        for (int i = 0, j=1; j < destinationCoordinates.size(); i++, j++) {
            try {
                result = DirectionsApi.newRequest(getGeoContext())
                        .mode(TravelMode.DRIVING).origin(destinationCoordinates.get(i))
                        .destination(destinationCoordinates.get(j))
                        .await();
                directionsResults.add(result);
            } catch (ApiException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return directionsResults;
    }

    private int getTravelTime(ArrayList<DirectionsResult> directionsResults) {
        int travelTime = 0;
        for (int i = 0; i < directionsResults.size(); i++) {
            DirectionsLeg[] legs = directionsResults.get(i).routes[0].legs;
            for (int j = 0; j < legs.length; j++) {
                travelTime += legs[j].duration.inSeconds;
            }
        }
        travelTime = Math.round(travelTime / 60);
        return travelTime;
    }

    private void addPolyline(ArrayList<DirectionsResult> directionsResults, GoogleMap mMap) {
        List<LatLng> decodedPath;
        for (int i = 0; i < directionsResults.size(); i++) {
            for (int j = 0; j < directionsResults.get(i).routes.length; j++) {
                decodedPath = PolyUtil.decode(directionsResults.get(i).routes[j].overviewPolyline.getEncodedPath());
                mMap.addPolyline(new PolylineOptions().color(randomColorGenerator()).addAll(decodedPath));
            }
        }
    }

    public void updateLocation(String s) throws InterruptedException, ApiException, IOException {
        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey(getResources().getString(R.string.maps_api_key))
                .build();
        GeocodingResult[] results =  GeocodingApi.geocode(context, s).await();
        lat = results[0].geometry.location.lat;
        lng = results[0].geometry.location.lng;
        mLocation.setLatitude(lat);
        mLocation.setLongitude(lng);
        mOnLocationUpdated.setLocation(mLocation);
    }

    public interface OnLocationUpdateListener {
        void setLocation(Location location);
    }

    public interface OnTravelTimeUpdatedListener {
        void updateTravelTime(int i);
    }

    public interface OnItineraryMarkerClicked {
        void scrollItinerary(String s);
    }

    public void setLocation(Location loc) {
        mLocation = loc;
    }

    public int randomColorGenerator() {
        Random rand = new Random();
        int red = rand.nextInt(256);
        int green = rand.nextInt(256);
        int blue = rand.nextInt(256);

        return Color.rgb(red, green, blue);
    }

    public BitmapDescriptor getMarkerIcon(int colorId) {
        int color = getResources().getColor(colorId);
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return BitmapDescriptorFactory.defaultMarker(hsv[0]);
    }

    public void showRecalculateButton() {
        mRecalculate.setVisibility(View.VISIBLE);
    }

    public void hideRecalculateButton() {
       mRecalculate.setVisibility(View.INVISIBLE);
    }
}
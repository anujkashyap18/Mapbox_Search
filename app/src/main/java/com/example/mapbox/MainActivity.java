package com.example.mapbox;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.MapboxDirections;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import timber.log.Timber;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;

@SuppressWarnings ( "deprecation" )
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener {
	
	private static final String ROUTE_LAYER_ID = "route-layer-id";
	private static final String ROUTE_SOURCE_ID = "route-source-id";
	private static final String ICON_SOURCE_ID = "icon-source-id";
	String[] perms = { Manifest.permission.INTERNET , Manifest.permission.ACCESS_FINE_LOCATION };
	MapView mapView;
	Point origin;
	Point destination;
	DirectionsRoute currentRoute;
	MapboxDirections client;
	MapboxMap mapbox;
	String mkey = "";
	ImageView search;
	PlaceOptions.Builder placeOptions;
	CarmenFeature feature;
	Marker current, dest;
	LocationManager locationManager;
	int x = 0;
	
	@Override
	protected void onCreate ( Bundle savedInstanceState ) {
		super.onCreate ( savedInstanceState );
		
		mkey = getString ( R.string.map_box_key );
		Mapbox.getInstance ( this , mkey );
		setContentView ( R.layout.activity_main );
		
		mapView = findViewById ( R.id.mapView );
		search = findViewById ( R.id.search );
		
		mapView.onCreate ( savedInstanceState );
		mapView.getMapAsync ( this );
		
		placeOptions = PlaceOptions.builder ( );
		placeOptions.limit ( 10 )
				.country ( "IN" )
				.backgroundColor ( this.getResources ( ).getColor ( R.color.white ) )
				.build ( PlaceOptions.MODE_CARDS );
		
		search.setOnClickListener ( view -> {
			Intent intent = new PlaceAutocomplete.IntentBuilder ( )
					.accessToken ( mkey )
					.placeOptions ( placeOptions.build ( ) )
					.build ( MainActivity.this );
			startActivityForResult ( intent , 11 );
		} );
		
		locationManager = ( LocationManager ) getSystemService ( Context.LOCATION_SERVICE );
	}
	
	@Override
	protected void onActivityResult ( int requestCode , int resultCode , Intent data ) {
		super.onActivityResult ( requestCode , resultCode , data );
		if ( resultCode == Activity.RESULT_OK && requestCode == 11 ) {
			feature = PlaceAutocomplete.getPlace ( data );
			
			if ( mapbox != null ) {
				if ( ActivityCompat.checkSelfPermission ( this , Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED
						&& ActivityCompat.checkSelfPermission ( this , Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
					return;
				}
				
				Location locationGPS = locationManager.getLastKnownLocation ( LocationManager.NETWORK_PROVIDER );
				double lat = locationGPS.getLatitude ( );
				double lon = locationGPS.getLongitude ( );
				
				origin = Point.fromLngLat ( lon , lat );
				destination = Point.fromLngLat ( ( ( Point ) feature.geometry ( ) ).longitude ( ) , ( ( Point ) feature.geometry ( ) ).latitude ( ) );
			}
			mapbox.setStyle ( Style.MAPBOX_STREETS , style -> {
				
				IconFactory iconFactory = IconFactory.getInstance ( MainActivity.this );
				Icon pickup = iconFactory.fromBitmap ( bitmapDescriptorFromVector ( MainActivity.this ) );
				Icon dropoff = iconFactory.fromBitmap ( bitmapDescriptorFromVector ( MainActivity.this ) );
				initSource ( style );
				initLayers ( style );
				getRoute ( mapbox , origin , destination );
				
				mapbox.animateCamera ( CameraUpdateFactory.newLatLngZoom ( new LatLng ( origin.latitude ( ) , origin.longitude ( ) ) , 11 ) , 1200 );
				
				current = new Marker (
						new MarkerOptions ( ).title ( "Current Location" ).position (
								new LatLng ( origin.latitude ( ) , origin.longitude ( ) ) ).icon ( pickup ) );
				
				mapbox.updateMarker ( current );
				
				if ( x == 0 ) {
					dest = mapbox.addMarker (
							new MarkerOptions ( ).title ( "Destination" )
									.position ( new LatLng ( destination.latitude ( ) , destination.longitude ( ) ) ).icon ( dropoff ) );
					x++;
				}
				else {
					mapbox.removeMarker ( dest );
					dest = mapbox.addMarker (
							new MarkerOptions ( ).title ( "Destination" )
									.position ( new LatLng ( destination.latitude ( ) , destination.longitude ( ) ) ).icon ( dropoff ) );
					mapbox.updateMarker ( dest );
				}
			} );
		}
	}
	
	@Override
	public void onMapReady ( @NonNull MapboxMap mapboxMap ) {
		mapbox = mapboxMap;
		
		mapboxMap.setStyle ( Style.MAPBOX_STREETS , ( Style style ) -> {
			
			LocationComponent locationComponent = mapboxMap.getLocationComponent ( );
			
			locationComponent.activateLocationComponent (
					LocationComponentActivationOptions.builder ( this , style ).build ( ) );
			if ( ActivityCompat.checkSelfPermission ( this , Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission ( this , Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
				// TODO: Consider calling
				//    ActivityCompat#requestPermissions
				// here to request the missing permissions, and then overriding
				//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
				//                                          int[] grantResults)
				// to handle the case where the user grants the permission. See the documentation
				// for ActivityCompat#requestPermissions for more details.
				return;
			}
			locationComponent.setLocationComponentEnabled ( true );
			locationComponent.setCameraMode ( CameraMode.TRACKING );
			
			locationComponent.setRenderMode ( RenderMode.COMPASS );
			
		} );
	}
	
	private Bitmap bitmapDescriptorFromVector ( Context mainActivity ) {
		Drawable background = ContextCompat.getDrawable ( mainActivity , R.drawable.pin );
		background.setBounds ( 0 , 0 , background.getIntrinsicWidth ( ) , background.getIntrinsicHeight ( ) );
		Bitmap bitmap = Bitmap.createBitmap ( background.getIntrinsicWidth ( ) , background.getIntrinsicHeight ( ) , Bitmap.Config.ARGB_8888 );
		Canvas canvas = new Canvas ( bitmap );
		background.draw ( canvas );
		return bitmap;
	}
	
	private void initSource ( @NonNull Style loadedMapStyle ) {
		loadedMapStyle.addSource ( new GeoJsonSource ( ROUTE_SOURCE_ID ) );
		GeoJsonSource iconGeoJsonSource = new GeoJsonSource ( ICON_SOURCE_ID , FeatureCollection.fromFeatures ( new Feature[] {
				Feature.fromGeometry ( Point.fromLngLat ( origin.longitude ( ) , origin.latitude ( ) ) ) ,
				Feature.fromGeometry ( Point.fromLngLat ( destination.longitude ( ) , destination.latitude ( ) ) ) } ) );
		loadedMapStyle.addSource ( iconGeoJsonSource );
	}
	
	private void initLayers ( @NonNull Style loadedMapStyle ) {
		LineLayer routeLayer = new LineLayer ( ROUTE_LAYER_ID , ROUTE_SOURCE_ID );
		
		routeLayer.setProperties (
				lineCap ( Property.LINE_CAP_ROUND ) ,
				lineJoin ( Property.LINE_JOIN_ROUND ) ,
				lineWidth ( 5f ) ,
				lineColor ( Color.parseColor ( "#ffbc01" ) )
		);
		loadedMapStyle.addLayer ( routeLayer );
	}
	
	private void getRoute ( final MapboxMap mapboxMap , Point origin , Point destination ) {
		Toast.makeText ( this , "get route" , Toast.LENGTH_SHORT ).show ( );
		client = MapboxDirections.builder ( )
				.origin ( origin )
				.destination ( destination )
				.overview ( DirectionsCriteria.OVERVIEW_FULL )
				.profile ( DirectionsCriteria.PROFILE_DRIVING )
				.accessToken ( getString ( R.string.map_box_key ) )
				.build ( );
		
		client.enqueueCall ( new Callback < DirectionsResponse > ( ) {
			@Override
			public void onResponse ( @NotNull Call < DirectionsResponse > call , retrofit2.@NotNull Response < DirectionsResponse > response ) {
				Timber.d ( "Response code : %s" , response.code ( ) );
				if ( response.body ( ) == null ) {
					Timber.d ( "No routes found, make sure you set the right user and access token." );
					return;
				}
				else if ( response.body ( ).routes ( ).size ( ) < 1 ) {
					Timber.d ( "No routes found" );
					return;
				}
				currentRoute = response.body ( ).routes ( ).get ( 0 );
				
				
				if ( mapboxMap != null ) {
					mapboxMap.getStyle ( style -> {
						GeoJsonSource source = style.getSourceAs ( ROUTE_SOURCE_ID );
						if ( source != null ) {
							source.setGeoJson ( LineString.fromPolyline ( currentRoute.geometry ( ) , 6 ) );
							Toast.makeText ( MainActivity.this , "Current Route : " + currentRoute.distance ( ) , Toast.LENGTH_SHORT ).show ( );
						}
					} );
				}
			}
			
			@Override
			public void onFailure ( @NotNull Call < DirectionsResponse > call , @NotNull Throwable throwable ) {
				Timber.d ( "Error : %s" , throwable.getMessage ( ) );
				Toast.makeText ( MainActivity.this , "Error: " + throwable.getMessage ( ) , Toast.LENGTH_SHORT ).show ( );
			}
		} );
	}
	
	@Override
	public void onExplanationNeeded ( List < String > permissionsToExplain ) {
	
	}
	
	@Override
	public void onPermissionResult ( boolean granted ) {
	
	}
}
package com.lucasdnd.mapwars;

import java.util.ArrayList;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.LocationSource.OnLocationChangedListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.lucasdnd.mapwars.game.CollisionChecker;
import com.lucasdnd.mapwars.game.MapCircle;
import com.lucasdnd.mapwars.game.LocationRandomizer;
import com.lucasdnd.mapwars.maps.GeometryUtil;
import com.lucasdnd.mapwars.maps.GridTileProvider;
import com.lucasdnd.mapwars.views.FireBarAnimation;
import com.lucasdnd.mapwars.views.OnHoldDownListener;

import android.app.FragmentManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.R.integer;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

public class MainActivity extends Activity implements OnCameraChangeListener, LocationListener {

	private static final String TAG = MainActivity.class.getSimpleName();
	// Map
	private GoogleMap map;
	private GridTileProvider gridTileProvider;
	private int playZoomLevel = 13;

	// Enemies
	private ArrayList<MapCircle> targets;

	// Control Mode. Camera mode allows map gestures
	private int currentMode = 0;
	private final int CAMERA_MODE = 0;
	private final int TARGET_MODE = 1;
	private final int FIRE_MODE = 2;

	// User Location
	private Location userLocation;

	// Views
	private Button rotateRightButton, rotateLeftButton, changeModeButton, fireButton;
	private View fireBar, fireBarBackground;
	private FireBarAnimation fireBarAnimation;
	private View marker1, marker2, marker3, marker4;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Setup Google Maps
		this.setupMap();

	}

    private void setupGame() {
        // Create the TileProvider
        gridTileProvider = new GridTileProvider(Color.argb(128,0,128,128), 1f);

        // Add the Tile Overlay to the map using our Grid Tile Provider
        map.addTileOverlay(new TileOverlayOptions ().tileProvider (gridTileProvider));

        // Add a Camera Listener. We use the Camera Listener to update the Current Location used by the Tile Provider.
        // The Tile Provider needs an updated location to calculate the Grid heights at different Latitudes.
        map.setOnCameraChangeListener(this);

        // Enemies!
        targets = new ArrayList<MapCircle>();

        // Setup views
        this.setupViews();
    }

	@Override
	protected void onResume() {
		super.onResume();

		// Request locations again
		this.requestLocations();
	}

	/**
	 * Button Actions
	 */
	private void setupViews() {

		marker1 = (View) this.findViewById(R.id.mainActivity_fireBarMarker1);
		marker2 = (View) this.findViewById(R.id.mainActivity_fireBarMarker2);
		marker3 = (View) this.findViewById(R.id.mainActivity_fireBarMarker3);
		marker4 = (View) this.findViewById(R.id.mainActivity_fireBarMarker4);

		rotateRightButton = (Button) this.findViewById(R.id.mainActivity_rotateRightButton);
		rotateRightButton.setOnTouchListener(new OnHoldDownListener(map, +0.01f));

		rotateLeftButton = (Button) this.findViewById(R.id.mainActivity_rotateLeftButton);
		rotateLeftButton.setOnTouchListener(new OnHoldDownListener(map, -0.01f));

		fireBar = (View) this.findViewById(R.id.mainActivity_fireBar);
		fireBarAnimation = new FireBarAnimation(fireBar, 600);
		fireBarAnimation.setDuration(2000);
		fireBar.setAnimation(fireBarAnimation);

		fireBarBackground = (View) this.findViewById(R.id.mainActivity_fireBarBackground);

		fireButton = (Button) this.findViewById(R.id.mainActivity_fireButton);
		fireButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if(currentMode == TARGET_MODE) {

					enterFireMode();

				} else if(currentMode == FIRE_MODE) {

					// Shoot!
					shoot();

					// Go back to Target Mode
					enterTargetMode();
				}
			}

		});

		changeModeButton = (Button) this.findViewById(R.id.mainActivity_changeModeButton);
		changeModeButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				// Which mode?
				if(currentMode == CAMERA_MODE) {

					// Do we have the User Location?
					if(userLocation != null) {

						enterTargetMode();

					} else {

						// Request location again
						requestLocations();

						// Show an alert saying we need the User Location
						new AlertDialog.Builder(v.getContext())
							.setTitle("Location Services")
							.setMessage("Need location to enter Fire mode")
							.create()
							.show();
					}

				} else {

					enterCameraMode();
				}
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * Starts Google Maps and set basic stuff
	 */
	private void setupMap() {

		// Check if we already have a Map
		if(map == null) {

            // Get the Map View
            ((MapFragment) getFragmentManager().findFragmentById(R.id.mainActivity_map)).getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    // Check if we're good to go
                    if (googleMap == null) {
                        System.out.println("Could not start the Map");
                        return;
                    }

                    map = googleMap;

                    // Enable My Location
                    map.setMyLocationEnabled(true);

                    setupGame();
                }
            });
        }
	}

	/**
	 * Shoot!
	 */
	private void shoot() {

		// Get the firepower
		int maxRange = 5000; // weapon max range
		double explosionRadius = 250.0;
		int maxBarHeight = 600;
		float firepower = (fireBar.getLayoutParams().height * maxRange) / maxBarHeight;

		// Create the Explosion Circle
		MapCircle explosion = new MapCircle(
				GeometryUtil.getLatLngAwayFromSource(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()),
						firepower, map.getCameraPosition().bearing), explosionRadius, Color.argb(255, 255, 128, 0));

		// Add it to the Map!
		explosion.setCircle(map.addCircle(explosion.getCircleOptions()));

		// Check if it collided with an Enemy
		CollisionChecker.checkCollision(explosion.getCircle(), targets);

		// Remove the ones who got hit and check if we won
		boolean win = true;
		for(MapCircle target : targets) {
			if(target.gotHit()) {
				target.getCircle().remove();
			} else {
				win = false;
			}
		}

		// Check if we won
		if(win) {
			rotateLeftButton.setBackgroundColor(Color.rgb(255, 0, 0));
			rotateLeftButton.setText("YOU");

			rotateRightButton.setBackgroundColor(Color.rgb(255, 0, 0));
			rotateRightButton.setText("WIN");
		}
	}

	/**
	 * Enter Fire Mode
	 */
	private void enterFireMode() {

		currentMode = FIRE_MODE;

		fireButton.setText("FIRE!");
		changeModeButton.setVisibility(View.GONE);
		rotateLeftButton.setVisibility(View.GONE);
		rotateRightButton.setVisibility(View.GONE);
		fireBar.setBackgroundColor(Color.argb(255, 255, 0, 0));
		fireBar.setVisibility(View.VISIBLE);
		fireBarBackground.setVisibility(View.VISIBLE);
		marker1.setVisibility(View.VISIBLE);
		marker2.setVisibility(View.VISIBLE);
		marker3.setVisibility(View.VISIBLE);
		marker4.setVisibility(View.VISIBLE);

		// Animate the Fire Bar
		fireBarAnimation.start();
	}

	/**
	 * Enter Camera Mode
	 */
	private void enterCameraMode() {

		// Camera Mode!
		currentMode = CAMERA_MODE;

		// Do a slight zoom out
		CameraPosition cameraPos = new CameraPosition(new LatLng(map.getCameraPosition().target.latitude, map.getCameraPosition().target.longitude), playZoomLevel - 1, 90, map.getCameraPosition().bearing);
		map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPos));

		// Enable map gestures
		map.getUiSettings().setAllGesturesEnabled(true);

		// Hide the Targeting stuffs
		changeModeButton.setVisibility(View.VISIBLE);
		rotateRightButton.setVisibility(View.GONE);
		rotateLeftButton.setVisibility(View.GONE);
		fireButton.setVisibility(View.GONE);
		fireBar.setVisibility(View.GONE);
		fireBarBackground.setVisibility(View.GONE);
		marker1.setVisibility(View.GONE);
		marker2.setVisibility(View.GONE);
		marker3.setVisibility(View.GONE);
		marker4.setVisibility(View.GONE);

		changeModeButton.setText("Click to enter Fire mode");
	}

	/**
	 * Enter Targeting Mode
	 */
	private void enterTargetMode() {

		// Target Mode!
		currentMode = TARGET_MODE;

		// Lock Position!
		CameraPosition cameraPos = new CameraPosition(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()), playZoomLevel, 90, map.getCameraPosition().bearing);
		map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPos));

		// Disable map gestures
		map.getUiSettings().setAllGesturesEnabled(false);

		// Show the Targeting stuff
		changeModeButton.setVisibility(View.VISIBLE);
		rotateRightButton.setVisibility(View.VISIBLE);
		rotateLeftButton.setVisibility(View.VISIBLE);
		fireButton.setVisibility(View.VISIBLE);
		fireBar.setBackgroundColor(Color.argb(0, 0, 0, 0));
		fireBar.setVisibility(View.GONE);
		fireBarBackground.setVisibility(View.GONE);
		marker1.setVisibility(View.GONE);
		marker2.setVisibility(View.GONE);
		marker3.setVisibility(View.GONE);
		marker4.setVisibility(View.GONE);

		changeModeButton.setText("Click to enter Camera mode");
		fireButton.setText("fire?");
	}

	/**
	 * Request a single location update from both GPS and Network
	 */
	private void requestLocations() {
		LocationManager locationManager = (LocationManager)this.getBaseContext().getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
		locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, this, null);
	}
	
	
	/**
	 * Creates targets around the user
	 * 
	 * @param numTargets
	 * @param distance
	 */
	private void createTargets(LatLng userLatLng, int numTargets) {
		
		for(int i = 0; i < numTargets; i++) {
			
			// Create the Targets
			MapCircle target = new MapCircle(LocationRandomizer.getRandomLatLng(userLatLng, 5000.0, 1000.0), 150.0, Color.argb(255, 0, 128, 255));
			target.setCircle(map.addCircle(target.getCircleOptions()));
			targets.add(target);
		}
	}

	@Override
	public void onCameraChange(CameraPosition position) {
		
		// Tell the TileOverlay where we are
		gridTileProvider.setCurrentLatLng(map.getCameraPosition().target);		
	}

	@Override
	public void onLocationChanged(Location location) {
		
		// Save the User Location
		userLocation = location;
		
		// Go to the User Location
		CameraPosition cameraPos = new CameraPosition(new LatLng(location.getLatitude(), location.getLongitude()), playZoomLevel - 1, 90, 0);
		map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPos));
		
		// Create targets now that we know the user location
		this.createTargets(new LatLng(location.getLatitude(), location.getLongitude()), 5);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {}

	@Override
	public void onProviderEnabled(String provider) {}

	@Override
	public void onProviderDisabled(String provider) {}
}

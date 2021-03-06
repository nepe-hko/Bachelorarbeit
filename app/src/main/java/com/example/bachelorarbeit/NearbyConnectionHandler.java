package com.example.bachelorarbeit;

import android.content.Context;
import androidx.annotation.NonNull;
import com.example.bachelorarbeit.test.TestServer;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import java9.util.concurrent.CompletableFuture;

public class NearbyConnectionHandler implements Discoverer {

    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final String SERVICE_ID  = "hko_app";
    private final String myID;
    private final Map<String,String> connectedDevices;
    private final Map<String,String> pendingDevices;
    private final NearbyReceiver receiver;
    private final DiscoveryTimer discoveryDiscoveryTimer;
    private final ConnectionsClient connectionsClient;
    private final ConnectionLifecycleCallback clc = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointID, @NonNull ConnectionInfo connectionInfo) {
            pendingDevices.put(endpointID, connectionInfo.getEndpointName());
            acceptConnection(endpointID);
        }

        @Override
        public void onConnectionResult(@NonNull String nearbyID, @NonNull ConnectionResolution connectionResolution) {

            String userID = pendingDevices.get(nearbyID);

            if(connectionResolution.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                receiver.onDeviceConnected(userID);
                connectedDevices.put(userID, nearbyID);
                TestServer.echo("connected to " + userID);
            }
            pendingDevices.remove(userID);
        }

        @Override
        public void onDisconnected(@NonNull String nearbyID) {
            try {
                String userID = getUserIDbyNearbyID(nearbyID);
                TestServer.echo("disconnected from " + userID);
                connectedDevices.remove(userID);
            } catch (Exception e) {
                TestServer.echo("On Disconnected Error" + e.toString());
            }

        }
    };

    NearbyConnectionHandler(Context context, String myID, NearbyReceiver receiver) {
        this.myID = myID;
        this.receiver = receiver;
        this.connectedDevices = new HashMap<>();
        this.pendingDevices = new HashMap<>();
        this.discoveryDiscoveryTimer = new DiscoveryTimer(this);
        this.connectionsClient = Nearby.getConnectionsClient(context);
        startAdvertise();
    }


    @Override
    public void onDiscoveryTimerExpired() {
        connectionsClient.stopDiscovery();
        TestServer.echo("stop Discover");
    }

    /**
     * connects to given user
     * @param userID user to connect to
     * @return nearbyID from connected user
     */
    public CompletableFuture<String> connect(String userID) {

        // if already connected return nearbyID
        if(connectedDevices.containsKey(userID)) {
            return CompletableFuture.completedFuture(connectedDevices.get(userID));
        }

        // if not connected start Discover and return nearbyID when connected
        startDiscover();
        boolean end;
        return CompletableFuture.supplyAsync( () -> {

            while(true) {
                if (connectedDevices.containsKey(userID) ) break;
                if (!discoveryDiscoveryTimer.isDiscovery()) {
                    return null;
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return connectedDevices.get(userID);
        }).orTimeout(30,TimeUnit.SECONDS);

    }

    /**
     * connects to all devices in range
     */
    public void connectAll() {
        startDiscover();
    }

    /**
     * returns the userID of a user by its nearbyID
     * @param nearbyID
     * @return userID of the User
     */
    public String getUserIDbyNearbyID(String nearbyID) {
            return connectedDevices.entrySet()
                    .stream()
                    .filter(entry -> nearbyID.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst().get();
    }

    /**
     * starts Advertise
     */
    private void startAdvertise() {

        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();

        connectionsClient.startAdvertising(myID, SERVICE_ID, clc, options)
                .addOnSuccessListener( (Void unused) -> TestServer.echo("start Advertise..."))
                .addOnFailureListener( (Exception e) -> TestServer.echo("Advertise failed"));
    }

    /**
     * starts Discover
     * discovering stops automatically, after timer expired
     */
    private void startDiscover() {

        TestServer.echo("start Discover");

        discoveryDiscoveryTimer.start();

        EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {

            @Override
            public void onEndpointFound(@NonNull String nearbyID, @NonNull DiscoveredEndpointInfo discoveredEndpointInfo) {

                // only for testing
                if (myID.equals("MARCO") && discoveredEndpointInfo.getEndpointName().equals("DANIEL")) {
                    return;
                }
                if (myID.equals("MARCO") && discoveredEndpointInfo.getEndpointName().equals("ASTI")) {
                    return;
                }
                if (myID.equals("PATRICK") && discoveredEndpointInfo.getEndpointName().equals("ASTI")) {
                    return;
                }
                if (myID.equals("DANIEL") && discoveredEndpointInfo.getEndpointName().equals("MARCO")) {
                    return;
                }
                if (myID.equals("ASTI") && discoveredEndpointInfo.getEndpointName().equals("PATRICK")) {
                    return;
                }
                if (myID.equals("ASTI") && discoveredEndpointInfo.getEndpointName().equals("MARCO")) {
                    return;
                }

                if (myID.equals("PATRICK") && discoveredEndpointInfo.getEndpointName().equals("LUSH")) {
                    return;
                }
                if (myID.equals("LUSH") && discoveredEndpointInfo.getEndpointName().equals("PATRICK")) {
                    return;
                }
                // end testing code

                if (connectedDevices.containsValue(nearbyID) || pendingDevices.containsValue(nearbyID)){
                    return;
                }

                requestConnection(nearbyID, discoveredEndpointInfo);
            }

            @Override
            public void onEndpointLost(@NonNull String nearbyID) {
                try {
                    String userID = getUserIDbyNearbyID(nearbyID);
                    TestServer.echo("lost connection from " + userID);
                    connectedDevices.remove(userID);
                } catch (Exception e) {
                    TestServer.echo("Lost Connection Error" + e.toString());
                }
            }
        };

        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options);
    }

    /**
     * Accepts Connection for given endpointID
     * @param endpointID ID of endpoint whose connection should be accepted
     */
    private void acceptConnection( String endpointID) {
        connectionsClient.acceptConnection(endpointID, new PayloadCallback() {
            @Override
            public void onPayloadReceived(@NonNull String s, @NonNull Payload payload) {
                receiver.onReceive(payload.asBytes());

            }

            @Override
            public void onPayloadTransferUpdate(@NonNull String s, @NonNull PayloadTransferUpdate payloadTransferUpdate) {
                receiver.onPayloadTransferUpdate(s, payloadTransferUpdate);
            }

        })
                .addOnSuccessListener(aVoid -> {})
                .addOnFailureListener( aVoid -> TestServer.echo("could not accept connection to " + pendingDevices.get(endpointID))
                );
    }

    /**
     * Request connection for given endpointID
     * @param endpointID ID of the endpoint to which a connection should be requested
     * @param info Info to get username of endpoint
     */
    private void requestConnection(String endpointID, DiscoveredEndpointInfo info) {
        connectionsClient.requestConnection(myID, endpointID, clc)
                .addOnSuccessListener((Void unused) -> {})
                .addOnFailureListener((Exception e) -> TestServer.echo("request Connection to " + info.getEndpointName() + " failed"));
    }

    Map<String,String> getConnectedDevices() {
        return this.connectedDevices;
    }
}

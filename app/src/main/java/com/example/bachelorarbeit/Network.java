package com.example.bachelorarbeit;

import android.content.Context;
import android.widget.TextView;
import com.example.bachelorarbeit.test.TestServer;
import com.example.bachelorarbeit.types.DATA;
import com.example.bachelorarbeit.types.PayloadType;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionsClient;

public class Network implements NearbyReceiver {

    private final TextView receivedView;
    private final String myID;
    private final DSRRouter router;
    private final ConnectionsClient connectionsClient;
    private final NearbyConnectionHandler nearby;

    public Network(Context context, String username, TextView receivedView) {
        this.receivedView = receivedView;
        this.myID = username;
        this.nearby = new NearbyConnectionHandler(context, myID, this);
        this.router = new DSRRouter(context, this.nearby, myID);
        this.connectionsClient = Nearby.getConnectionsClient(context);
    }

    /**
     * sends a message to another device
     * @param userID username of receiver
     * @param message message to be transmitted
     */
    public void sendText(String userID, String message) {

        DATA dataPackage = new DATA(myID, userID, message);

        // get Route -> connect to next hop in route -> send Message to next hop
        router.getRoute(userID)
                .thenAccept(dataPackage::setRoute)
                .thenCompose( nextHop -> nearby.connect(dataPackage.getRoute().getNextHop(myID)))
                .thenAccept( nearbyID -> connectionsClient.sendPayload(nearbyID, dataPackage.serialize()));

    }

    /**
     * this function is called for incoming receivedDATA from other devices
     * @param data receivedDATA received from other device
     */
    @Override
    public void receive(byte[] data) {

        // unpack payload
        Object payload = PayloadType.deserialize(data);
        assert payload != null;

        // forward all messages to router
        router.receive(payload);

        // messages intended for this device are displayed
        String payloadType = payload.getClass().getSimpleName();
        if (payloadType.equals("DATA")) {
            DATA receivedData = (DATA)payload;
            if (receivedData.getDestinationID().equals(myID)) {
                String text = receivedData.getMessage();
                this.receivedView.setText(receivedData.getSourceID() + " -> " + myID + ": " + text + "\n" + receivedView.getText());
                TestServer.echo("received a Message " + text);
            }
        }
    }

    @Override
    public void onDeviceConnected(String userID) {
        router.deviceConnected(userID);
    }
}

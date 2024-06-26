package com.talosvfx.talos.runtime.routine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.*;
import com.talosvfx.talos.runtime.RuntimeContext;
import com.talosvfx.talos.runtime.assets.*;
import com.talosvfx.talos.runtime.scene.GameObject;
import lombok.Getter;

import java.util.UUID;

public abstract class RoutineNode implements Pool.Poolable, TalosContextProvider {

    protected RoutineInstance routineInstanceRef;

    GameAsset.GameAssetUpdateListener updateListener;

    private GameAsset<?> cachedGameAsset;
    public int uniqueId;

    protected boolean nodeDirty = false;
    protected JsonValue propertiesJson;
    @Getter
    protected boolean configured = false;
    private int configureDepth = 0;

    public enum DataType {
        NUMBER,
        VECTOR2,
        VECTOR3,
        COLOR,
        ASSET,
        STRING,
        BOOLEAN,
        FLUID,
        GAMEOBJECT
    }

    public enum PortType {
        INPUT,
        OUTPUT,
        NONE
    }

    public enum ConnectionType {
        SIGNAL,
        DATA
    }

    public class Connection {
        public Port fromPort;
        public Port toPort;
    }

    public class Port {
        public String name;
        public RoutineNode nodeRef;
        public PortType portType = PortType.NONE;
        public ConnectionType connectionType;
        public DataType dataType;
        public Array<Connection> connections = new Array<>();
        public Object valueOverride;

        public void setValueFromString(String val) {
            if(dataType == DataType.FLUID) {
                valueOverride = Float.parseFloat(val);
            } else if (dataType == DataType.NUMBER) {
                valueOverride = Float.parseFloat(val);
            } else {
                valueOverride = val;
            }
        }

        public void setValue(Object object) {
            valueOverride = object;
        }
    }

    protected ObjectMap<String, Port> inputs = new ObjectMap<>();
    protected ObjectMap<String, Port> outputs = new ObjectMap<>();

    public RoutineNode() {
        updateListener = new GameAsset.GameAssetUpdateListener() {
            @Override
            public void onUpdate () {
                RoutineNode.this.routineInstanceRef.setDirty();
                RoutineNode.this.nodeDirty = true;
            }
        };
    }

    public void loadFrom(RoutineInstance routineInstance, JsonValue nodeData) {
        routineInstanceRef = routineInstance;

        // load properties
        String name = nodeData.getString("name");
        XmlReader.Element config = routineInstanceRef.getConfig(name);
        constructNode(config);

        //todo: fix later?
        this.propertiesJson = nodeData.get("properties");
        scheduleConfiguring();

        uniqueId = nodeData.getInt("id");
    }

    private void scheduleConfiguring() {
        configureDepth++;
        configureNode(propertiesJson);
        if(!configured) {
            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    if(configureDepth < 5) {
                        scheduleConfiguring(); // try again next time
                    }
                }
            });
        }
    }

    protected void constructNode(XmlReader.Element config) {
        int rowCount = config.getChildCount();
        for (int i = 0; i < rowCount; i++) {
            XmlReader.Element row = config.getChild(i);
            processRow(row);
        }
    }

    private void processRow(XmlReader.Element row) {


        if(row.getName().equals("group")) {
            int rowCount = row.getChildCount();
            for (int i = 0; i < rowCount; i++) {
                processRow(row.getChild(i));
            }

            return;
        }

        String name = row.getAttribute("name");

        Port port = new Port();
        port.name = name;
        port.nodeRef = this;

        String portType = row.getAttribute("port", "");

        String type = row.getAttribute("type", "text");
        if(type.equals("signal")) {
            port.connectionType = ConnectionType.SIGNAL;
        } else {
            port.connectionType = ConnectionType.DATA;

            if(type.equals("int")) port.dataType = DataType.NUMBER;
            if(type.equals("float")) port.dataType = DataType.NUMBER;
            if(type.equals("vec2")) port.dataType = DataType.VECTOR2;
            if(type.equals("vec3")) port.dataType = DataType.VECTOR3;
            if(type.equals("color")) port.dataType = DataType.COLOR;
            if(type.equals("asset")) port.dataType = DataType.ASSET;
            if(type.equals("SKELETON")) port.dataType = DataType.ASSET;
            if(type.equals("ROUTINE")) port.dataType = DataType.ASSET;
            if(type.equals("SOUND")) port.dataType = DataType.ASSET;
            if(type.equals("SCENE")) port.dataType = DataType.ASSET;
            if(type.equals("PREFAB")) port.dataType = DataType.ASSET;
            if(type.equals("VFX")) port.dataType = DataType.ASSET;
            if(type.equals("SPRITE")) port.dataType = DataType.ASSET;
            if(type.equals("text")) port.dataType = DataType.STRING;
            if(type.equals("fluid")) port.dataType = DataType.FLUID;
            if(type.equals("gameobject")) port.dataType = DataType.GAMEOBJECT;
            if(name.equals("asset")) port.dataType = DataType.ASSET;


            if(row.getName().equals("checkbox")) {
                port.valueOverride = row.getBooleanAttribute("default", false);
            }
        }

        if(portType.equals("input")) {
            port.portType = PortType.INPUT;
            inputs.put(name, port);
        } else if(portType.equals("output")) {
            port.portType = PortType.OUTPUT;
            outputs.put(name, port);
        } else {
            inputs.put(name, port);
        }
    }

    protected void configureNode(JsonValue properties) {
        for(JsonValue item: properties) {
            String name = item.name;

            Port port = inputs.get(name);
            if(port == null) {
                configured = false;
                return;
            }
            JsonValue jsonValue = properties.get(name);
            if(port.dataType == DataType.COLOR) {
                Json json = new Json();
                Color color = json.readValue(Color.class, jsonValue);
                port.setValue(color);
            } else if(port.dataType == DataType.VECTOR2) {
                float x = jsonValue.getFloat("x");
                float y = jsonValue.getFloat("y");
                Vector2 vec = new Vector2(x, y);
                port.valueOverride = vec;
            } else if(port.dataType == DataType.ASSET) {
                Json json = new Json();
                try {
                    jsonValue.addChild("talosIdentifier", new JsonValue(talosIdentifier));
                    GameAsset<?> gameAsset;

                    //new way
                    gameAsset = GameResourceOwner.readAsset(json, jsonValue);

                    //legacy
                    if (gameAsset.isBroken()) {
                        GameAssetType type = json.readValue("type", GameAssetType.class, jsonValue);

                        BaseAssetRepository baseAssetRepository = RuntimeContext.getInstance().getTalosContext(talosIdentifier).getBaseAssetRepository();

                        UUID uuid = readUUIDFromData(jsonValue);
                        if (uuid == null) {
                            String id = jsonValue.getString("id", "broken");
                            gameAsset = baseAssetRepository.getAssetForIdentifier(id, type);
                        } else {
                            gameAsset = baseAssetRepository.getAssetForUniqueIdentifier(uuid, type);
                        }
                    }
                    port.setValue(gameAsset);

                    if(gameAsset.isBroken()) {
                        configured = false;
                        return;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    String str = properties.getString(name);
                    if(str == null || str.isEmpty()) {
                        // we keep value override
                    } else {
                        port.setValueFromString(str);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        configured = true;
    }


    public void addConnection(RoutineNode toNode, String fromSlot, String toSlot) {
        Port fromPort = outputs.get(fromSlot);
        Port toPort = toNode.getInputPort(toSlot);

        Connection leftConnection = new Connection();
        Connection rightConnection = new Connection();

        leftConnection.fromPort = fromPort;
        leftConnection.toPort = toPort;

        rightConnection.fromPort = toPort;
        rightConnection.toPort = fromPort;

        fromPort.connections.add(leftConnection);
        toPort.connections.add(rightConnection);
    }

    private Port getInputPort(String name) {
        return inputs.get(name);
    }

    /**
     * I just got signal to one of my ports
     * @param portName
     */
    public void receiveSignal(String portName) {

    }

    /**
     * Send signal to all connections from particular output port
     * @param portName
     */
    public void sendSignal(String portName) {
        Port port = outputs.get(portName);
        if(port != null) {
            if(port.connectionType == ConnectionType.SIGNAL) {
                for(Connection connection: port.connections) {
                    RoutineNode targetNode = connection.toPort.nodeRef;
                    String targetName = connection.toPort.name;

                    Object payload = routineInstanceRef.getSignalPayload();
                    Object targets = routineInstanceRef.fetchGlobal("executedTargets");
                    targetNode.receiveSignal(targetName);
                    routineInstanceRef.setSignalPayload(payload);
                    routineInstanceRef.storeGlobal("executedTargets", targets);
                }
            }
        }

        routineInstanceRef.onSignalSent(uniqueId, portName);
    }

    protected GameAsset<?> fetchAssetValue(String key) {
        GameAsset<?> gameAsset = null;
        Port port = inputs.get(key);

        if (port.connections.isEmpty()) {
            if (port.valueOverride instanceof GameAsset) {
                gameAsset = (GameAsset<?>) (port.valueOverride);
            }
        } else {
            Connection connection = port.connections.first();
            RoutineNode targetNode = connection.toPort.nodeRef;
            String targetPortName = connection.toPort.name;

            if (targetNode.queryValue(targetPortName) instanceof GameAsset) {
                gameAsset = (GameAsset<?>) targetNode.queryValue(targetPortName);
            }
        }

        if (cachedGameAsset == gameAsset) {
            return gameAsset;
        } else {
            if (cachedGameAsset != null) {
                cachedGameAsset.listeners.removeValue(updateListener, true);
            }
            cachedGameAsset = gameAsset;
            if (gameAsset != null) {
                if (!gameAsset.listeners.contains(updateListener, true)) {
                    gameAsset.listeners.add(updateListener);
                }
            }
        }

        return cachedGameAsset;
    }

    protected String fetchStringValue(String key) {
        Port port = inputs.get(key);

        if(port == null) return "";

        return (String) port.valueOverride;
    }

    protected boolean isPortConnected(String key) {
        Port port = inputs.get(key);
        if(port == null) return false;
        if(port.connectionType == ConnectionType.DATA) {
            if (!port.connections.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    protected float fetchFloatValue(String key) {
        Object object = fetchValue(key);

        if(object instanceof Integer) {
            int result = (int) object;
            return (float) result;
        }

        if (!(object instanceof Float)) {
            return 0;
        }

        return (float)object;
    }

    protected Color fetchColorValue(String key) {
        Object object = fetchValue(key);
        if (!(object instanceof Color)) {
            return Color.WHITE;
        }

        return (Color)object;
    }

    protected boolean fetchBooleanValue(String key) {
        Object object = fetchValue(key);
        if(object == null) {
            return false;
        }

        if(object instanceof String) {
            boolean result = Boolean.parseBoolean((String) object);
            return result;
        }

        return (boolean)object;
    }

    protected int fetchIntValue(String key) {
        Object object = fetchValue(key);

        if(object instanceof Float) {
            float result = (float) object;
            return (int) Math.floor(result);
        }

        return (int)object;
    }
    protected GameObject fetchGameObjectValue (String key) {
        Object object = fetchValue(key);

        if (object instanceof GameObject) {
            return ((GameObject) object);
        }
        return null;
    }

    protected Vector2 fetchVector2Value(String key) {
        Object object = fetchValue(key);

        return (Vector2)object;
    }

    protected boolean isInputAndConnected (String key) {
        routineInstanceRef.setRequester(uniqueId);

        Port port = inputs.get(key);
        if(port == null) return false;

        if (port.connections.isEmpty()) return false;
        return true;
    }

    /**
     * Ask my input port for it's value
     * @param key
     * @return
     */
    protected Object fetchValue(String key) {

        routineInstanceRef.setRequester(uniqueId);

        Port port = inputs.get(key);

        if(port == null) return null;

        if(port.connectionType == ConnectionType.DATA) {
            if(!port.connections.isEmpty()) {
                Connection connection = port.connections.first();
                RoutineNode targetNode = connection.toPort.nodeRef;
                String targetPortName = connection.toPort.name;

                routineInstanceRef.onInputFetched(uniqueId, key);

                return targetNode.queryValue(targetPortName);
            } else {
                if(port.valueOverride == null && port.dataType == DataType.NUMBER) {
                    return 0f;
                }
                return port.valueOverride;
            }
        }

        return null;
    }

    /**
     * I have been asked from my output port to provide its value
     * @param targetPortName
     */
    public Object queryValue(String targetPortName) {

        return 0;
    }

    public void setProperty(String key, Object value) {
        if(inputs.containsKey(key)) {
            inputs.get(key).valueOverride = value;
        }
    }

    @Override
    public void reset() {

    }

    static UUID readUUIDFromData (JsonValue jsonValue) {
        String uuidString = jsonValue.getString("uuid", null);
        if (uuidString == null) {
            return null;
        } else {
            return UUID.fromString(uuidString);
        }
    }

    protected transient String talosIdentifier;

    @Override
    public void setTalosIdentifier (String identifier) {
        this.talosIdentifier = identifier;
    }

    @Override
    public String getTalosIdentifier () {
       return talosIdentifier;
    }

    public RoutineEventInterface getRoutineEventInterface () {
        return RuntimeContext.getInstance().getTalosContext(talosIdentifier).getRoutineDefaultEventInterface();
    }

}

package com.talosvfx.talos.editor.addons.scene.apps.tiledpalette;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pool;
import com.kotcrab.vis.ui.FocusManager;
import com.talosvfx.talos.TalosMain;
import com.talosvfx.talos.editor.addons.scene.MainRenderer;
import com.talosvfx.talos.editor.addons.scene.SceneEditorWorkspace;
import com.talosvfx.talos.editor.addons.scene.assets.GameAsset;
import com.talosvfx.talos.editor.addons.scene.events.GameObjectSelectionChanged;
import com.talosvfx.talos.editor.addons.scene.logic.GameObject;
import com.talosvfx.talos.editor.addons.scene.logic.TilePaletteData;
import com.talosvfx.talos.editor.addons.scene.logic.components.TransformComponent;
import com.talosvfx.talos.editor.addons.scene.maps.GridPosition;
import com.talosvfx.talos.editor.addons.scene.maps.StaticTile;
import com.talosvfx.talos.editor.addons.scene.maps.TalosLayer;
import com.talosvfx.talos.editor.notifications.Notifications;
import com.talosvfx.talos.editor.utils.GridDrawer;
import com.talosvfx.talos.editor.widgets.ui.ViewportWidget;

import java.util.UUID;
import java.util.function.Supplier;

import static com.talosvfx.talos.editor.addons.scene.SceneEditorWorkspace.ctrlPressed;

public class PaletteEditorWorkspace extends ViewportWidget {
    GameAsset<TilePaletteData> paletteData;

    private GridDrawer gridDrawer;
    private SceneEditorWorkspace.GridProperties gridProperties;

    private Image selectionRect;

    private MainRenderer mainRenderer;

    private Pool<PaletteEvent> paletteEventPool;

    public PaletteEditorWorkspace(GameAsset<TilePaletteData> paletteData) {
        super();
        this.paletteData = paletteData;
        setWorldSize(10f);
        setCameraPos(0, 0);

        mainRenderer = new MainRenderer();

        paletteEventPool = new Pool<PaletteEvent>() {
            @Override
            protected PaletteEvent newObject() {
                PaletteEvent e = new PaletteEvent();
                e.setTarget(PaletteEditorWorkspace.this);
                return e;
            }
        };

        gridProperties = new SceneEditorWorkspace.GridProperties();
        gridProperties.sizeProvider = new Supplier<float[]>() {
            @Override
            public float[] get () {
                if (SceneEditorWorkspace.getInstance().mapEditorState.isEditing()) {
                    TalosLayer selectedLayer = SceneEditorWorkspace.getInstance().mapEditorState.getLayerSelected();

                    if (selectedLayer == null) {
                        return new float[]{1,1};
                    } else {
                        return new float[]{selectedLayer.getTileSizeX(), selectedLayer.getTileSizeY()};
                    }
                }

                return new float[]{1, 1};

            }
        };

        gridDrawer = new GridDrawer(this, camera, gridProperties);
        gridDrawer.highlightCursorHover = true;

        selectionRect = new Image(TalosMain.Instance().getSkin().getDrawable("orange_row"));
        selectionRect.setSize(0, 0);
        selectionRect.setVisible(false);
        addActor(selectionRect);

        initListeners();
    }

    private void initListeners () {
        inputListener = new InputListener() {
            // selection stuff
            Vector2 startPos = new Vector2();
            Vector2 vec = new Vector2();
            Rectangle rectangle = new Rectangle();
            boolean upWillClear = true;
            private boolean isSelectingWithDrag = false;

            @Override
            public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
                paletteData.getResource().selectedGameAssets.clear();

                upWillClear = true;


                if(button == 2 || ctrlPressed()) {

                    isSelectingWithDrag = true;
                    selectionRect.setVisible(true);
                    selectionRect.setSize(0, 0);
                    startPos.set(x, y);

                    getStage().cancelTouchFocusExcept(this, PaletteEditorWorkspace.this);


                    event.handle();


                    return true;
                }

                return true;
            }

            @Override
            public void touchDragged (InputEvent event, float x, float y, int pointer) {
                super.touchDragged(event, x, y, pointer);

                if(selectionRect.isVisible()) {
                    vec.set(x, y);
                    vec.sub(startPos);
                    if(vec.x < 0) {
                        rectangle.setX(x);
                    } else {
                        rectangle.setX(startPos.x);
                    }
                    if(vec.y < 0) {
                        rectangle.setY(y);
                    } else {
                        rectangle.setY(startPos.y);
                    }
                    rectangle.setWidth(Math.abs(vec.x));
                    rectangle.setHeight(Math.abs(vec.y));

                    selectionRect.setPosition(rectangle.x, rectangle.y);
                    selectionRect.setSize(rectangle.getWidth(), rectangle.getHeight());

                    event.handle();
                }

            }

            @Override
            public void touchUp (InputEvent event, float x, float y, int pointer, int button) {

                if (!isSelectingWithDrag) {
                    //Find what we got on touch up and see
                    selectByPoint(x, y);
                }


                if(selectionRect.isVisible()) {
                    upWillClear = false;
                    selectByRect(rectangle);
                } else if(upWillClear) {
                    FocusManager.resetFocus(getStage());
//                    selectedGameAssets.clear(); //Dont need here
                } else {
                    if(!Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
                        // deselect all others, if they are selected
//                        if(deselectOthers(selectedGameObject)) {/ /todo
//                            Notifications.fireEvent(Notifications.obtainEvent(GameObjectSelectionChanged.class).set(selection));
//                        }
                    }
                }



                selectionRect.setVisible(false);
                isSelectingWithDrag = false;

                super.touchUp(event, x, y, pointer, button);
            }
        };

        addListener(inputListener);
    }

    @Override
    public void drawContent(Batch batch, float parentAlpha) {
        batch.end();
        gridDrawer.drawGrid();
        batch.begin();

        ObjectMap<UUID, float[]> positions = paletteData.getResource().positions;
        ObjectMap<UUID, GameAsset<?>> references = paletteData.getResource().references;

        ObjectMap<GameAsset<?>, StaticTile> staticTiles = paletteData.getResource().staticTiles;
        ObjectMap<GameAsset<?>, GameObject> gameObjects = paletteData.getResource().gameObjects;

        TalosLayer layerSelected = SceneEditorWorkspace.getInstance().mapEditorState.getLayerSelected();
        float tileSizeX = 1;
        float tileSizeY = 1;
        if (layerSelected != null) {
            tileSizeX = layerSelected.getTileSizeX();
            tileSizeY = layerSelected.getTileSizeY();
        }
        for (ObjectMap.Entry<GameAsset<?>, StaticTile> entry : staticTiles) {
            float[] pos = positions.get(entry.key.getRootRawAsset().metaData.uuid);

            StaticTile staticTile = entry.value;
            StaticTile value = staticTile;
            GridPosition gridPosition = value.getGridPosition();
            gridPosition.x = pos[0];
            gridPosition.y = pos[1];

            mainRenderer.renderStaticTileDynamic(staticTile, batch, tileSizeX, tileSizeY);
        }
        for (ObjectMap.Entry<GameAsset<?>, GameObject> entry : gameObjects) {
            float[] pos = positions.get(entry.key.getRootRawAsset().metaData.uuid);

            GameObject gameObject = entry.value;
            if (gameObject.hasComponent(TransformComponent.class)) {
                TransformComponent transform = gameObject.getComponent(TransformComponent.class);
                transform.position.set(pos[0], pos[1]);
            }

            mainRenderer.update(gameObject);
            mainRenderer.render(batch, new MainRenderer.RenderState(), gameObject);
        }

        batch.end();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.ORANGE);


        for (GameAsset<?> selectedGameAsset : paletteData.getResource().selectedGameAssets) {
            UUID uuid = selectedGameAsset.getRootRawAsset().metaData.uuid;
            float[] floats = positions.get(uuid);
            shapeRenderer.rect(floats[0] - 0.5f, floats[1] - 0.5f, 1, 1);
        }

        shapeRenderer.end();

        batch.begin();
    }

    private void selectByPoint (float x, float y) {
        Vector3 localPoint = new Vector3(x, y, 0);
        getWorldFromLocal(localPoint);

        // get list of entities that have their origin in the rectangle
        ObjectMap<GameAsset<?>, GameObject> gameObjects = paletteData.getResource().gameObjects;
        ObjectMap<GameAsset<?>, StaticTile> staticTiles = paletteData.getResource().staticTiles;
        ObjectMap<UUID, float[]> positions = paletteData.getResource().positions;

        paletteData.getResource().selectedGameAssets.clear();

        //find closest
        GameAsset<?> closestGameAsset = null;
        GameObject closestGameObject = null;
        PaletteEditor.PaletteFilterMode mode = PaletteEditor.PaletteFilterMode.NONE;
        for (ObjectMap.Entry<GameAsset<?>, GameObject> gameObjectEntry : gameObjects) {
            GameAsset<?> gameAsset = gameObjectEntry.key;
            UUID gameAssetUUID = gameAsset.getRootRawAsset().metaData.uuid;
            GameObject gameObject = gameObjectEntry.value;

            float[] pos = positions.get(gameAssetUUID);

            float minDistance = 1;
            float squaredDistance = minDistance * minDistance;
            float currentClosestDistance = squaredDistance + 1;

            float squareDistanceToCheck = (float)(Math.pow(pos[0] - localPoint.x, 2) + Math.pow(pos[1] - localPoint.y, 2));
            if (squareDistanceToCheck < squaredDistance) {
                if (squareDistanceToCheck < currentClosestDistance) {
                    closestGameAsset = gameAsset;
                    mode = PaletteEditor.PaletteFilterMode.ENTITY;
                    closestGameObject = gameObject;
                }
            }
        }
        for (ObjectMap.Entry<GameAsset<?>, StaticTile> staticTileEntry : staticTiles) {
            GameAsset<?> gameAsset = staticTileEntry.key;
            UUID gameAssetUUID = gameAsset.getRootRawAsset().metaData.uuid;
            StaticTile staticTile = staticTileEntry.value;

            float[] pos = positions.get(gameAssetUUID);

            float minDistance = 1;
            float squaredDistance = minDistance * minDistance;
            float currentClosestDistance = squaredDistance + 1;

            float squareDistanceToCheck = (float)(Math.pow(pos[0] - localPoint.x, 2) + Math.pow(pos[1] - localPoint.y, 2));
            if (squareDistanceToCheck < squaredDistance) {
                if (squareDistanceToCheck < currentClosestDistance) {
                    closestGameAsset = gameAsset;
                    mode = PaletteEditor.PaletteFilterMode.TILE;
                }
            }
        }
        // fire event more palette
        if (closestGameAsset != null) {
            paletteData.getResource().selectedGameAssets.add(closestGameAsset);
            PaletteEvent event = paletteEventPool.obtain();
            event.setType(PaletteEvent.Type.selected);
            event.setSelectedGameAssets(paletteData.getResource().selectedGameAssets);
            event.setCurrentFilterMode(mode);
            notify(event, false);

            if (closestGameObject != null) {
                SceneEditorWorkspace.getInstance().selectPropertyHolder(closestGameObject);
                Array<GameObject> gameObjectSelection = new Array<>();
                gameObjectSelection.add(closestGameObject);
                Notifications.fireEvent(Notifications.obtainEvent(GameObjectSelectionChanged.class).set(gameObjectSelection));
            }
        }
    }

    Vector3 lb = new Vector3();
    Vector3 lt = new Vector3();
    Vector3 rb = new Vector3();
    Vector3 rt = new Vector3();
    private void selectByRect(Rectangle rectangle) {
        Rectangle localRect = new Rectangle();
        lb.set(rectangle.x, rectangle.y, 0);
        lt.set(rectangle.x, rectangle.y + rectangle.height, 0);
        rb.set(rectangle.x + rectangle.width, rectangle.y, 0);
        rt.set(rectangle.x + rectangle.width, rectangle.y + rectangle.height, 0);
        getWorldFromLocal(lb);
        getWorldFromLocal(lt);
        getWorldFromLocal(rb);
        getWorldFromLocal(rt);
        localRect.set(lb.x, lb.y, Math.abs(rb.x - lb.x), Math.abs(lt.y - lb.y)); // selection rectangle in grid space

        // get list of entities that have their origin in the rectangle
        ObjectMap<GameAsset<?>, GameObject> gameObjects = paletteData.getResource().gameObjects;
        ObjectMap<GameAsset<?>, StaticTile> staticTiles = paletteData.getResource().staticTiles;
        ObjectMap<UUID, float[]> positions = paletteData.getResource().positions;

        paletteData.getResource().selectedGameAssets.clear();
        // entities
        for (ObjectMap.Entry<GameAsset<?>, GameObject> gameObjectEntry : gameObjects) {
            GameAsset<?> gameAsset = gameObjectEntry.key;
            UUID gameAssetUUID = gameAsset.getRootRawAsset().metaData.uuid;
            float[] pos = positions.get(gameAssetUUID);
            if(localRect.contains(pos[0], pos[1])) {
                paletteData.getResource().selectedGameAssets.add(gameAsset);
            }
        }
        // static tiles
        for (ObjectMap.Entry<GameAsset<?>, StaticTile> staticTileEntry : staticTiles) {
            GameAsset<?> gameAsset = staticTileEntry.key;
            UUID gameAssetUUID = gameAsset.getRootRawAsset().metaData.uuid;
            float[] pos = positions.get(gameAssetUUID);
            if(localRect.contains(pos[0], pos[1])) {
                paletteData.getResource().selectedGameAssets.add(gameAsset);
            }
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        camera.update();
    }
}

package com.talosvfx.talos.editor.addons.scene.apps.routines.runtime.nodes;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;
import com.talosvfx.talos.editor.addons.scene.apps.routines.runtime.AsyncRoutineNodeState;
import com.talosvfx.talos.editor.addons.scene.apps.routines.runtime.RoutineNode;
import lombok.Getter;

public abstract class AsyncRoutineNode<U, T extends AsyncRoutineNodeState<U>> extends RoutineNode {

    @Getter
    protected Array<T> states = new Array<>();

    private Array<U> tmpArr = new Array<>();

    private boolean isYoyo = false;

    // override fetching of variables, so that before fetching it sets "fetch payload",
    // so when fetching it uses that payload to provide proper data, which for example will be used by stagger node or other
    // this is similar to for depth info
    // in this case the payload here will be: GO, and it's index (idl if index should be somehow mixed with depth shit)

    protected T obtainState() {
        AsyncRoutineNodeState<U> state = Pools.obtain(AsyncRoutineNodeState.class);

        return (T) state;
    }

    @Override
    public void receiveSignal(String portName) {
        U signalPayload = (U)routineInstanceRef.getSignalPayload();
        T state = obtainState();
        state.setTarget(signalPayload);
        states.add(state);

        float duration = fetchFloatValue("duration");
        state.setDuration(duration);
        state.direction = 1;

        isYoyo = fetchBooleanValue("yoyo");

        targetAdded(state);
    }

    protected void targetAdded(T state) {

    }

    public void tick(float delta) {
        if(states.isEmpty()) return;

        // for each state process it's alpha
        // make sure to use interpolations
        // make sure to perform yoyo logic

        // delegate to some other tick method of whoever extends this so they can set some vars based on this alpha
        // make sure to provide state in question

        // when it's finished call the end signal


        tmpArr.clear();

        for(int i = states.size - 1; i >= 0; i--) {
            T state = states.get(i);
            state.alpha += state.direction * delta/state.getDuration();

            // todo: apply interpolations here
            if(state.alpha > 1) state.alpha = 1;
            if(state.alpha < 0) state.alpha = 0;

            stateTick(state, delta);

            if(state.alpha >= 1 && state.direction == 1) {
                if(!isYoyo) {
                    freeState(i);
                } else {
                    state.direction *= -1;
                }
            }

            if(state.alpha <= 0 && state.direction == -1) {
                freeState(i);
            }
        }

        for(U target: tmpArr) {
            // this now needs to send signal to next guy
            routineInstanceRef.setSignalPayload(target);
            sendSignal("onComplete");
        }
        tmpArr.clear();
    }

    private void freeState(int i) {
        T state = states.get(i);
        U target = state.getTarget();
        tmpArr.add(target);

        states.removeIndex(i);
        Pools.free(state);
    }

    protected abstract void stateTick(T state, float delta);
}

/*
 * Copyright (C) 2021 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package evervolv.app;

import java.util.List;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

public class GlobalActionManager {

    private Context mContext;

    private static IGlobalActions sService;
    private static GlobalActionManager sInstance;

    private static final String TAG = "GlobalActionManager";

    private GlobalActionManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }

        try {
            sService = getService();
        } catch (RemoteException e) {
            sService = null;
        }

        if (sService == null) {
            Log.wtf(TAG, "Unable to get LineageGlobalActionsService. The service either" +
                    " crashed, was not started, or the interface has been called to early in" +
                    " SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link lineageos.app.GlobalActionManager}
     * @param context
     * @return {@link GlobalActionManager}
     */
    public static GlobalActionManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new GlobalActionManager(context);
        }
        return sInstance;
    }

    /** @hide */
    static public IGlobalActions getService() throws RemoteException {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(
                ContextConstants.GLOBAL_ACTIONS_SERVICE);
        sService = IGlobalActions.Stub.asInterface(b);

        if (sService == null) {
            throw new RemoteException("Couldn't get " +
                    ContextConstants.GLOBAL_ACTIONS_SERVICE +
                    " on binder");
        }
        return sService;
    }


    /**
     * Update the action to the state.
     * @param enabled a {@link Boolean} value
     * @param action a {@link String} value
     */
    public void updateUserConfig(boolean enabled, String action) {
        try {
            getService().updateUserConfig(enabled, action);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the user configuration as {@link List<String>}.
     * @return {@link List<String>}
     */
    public List<String> getLocalUserConfig() {
        try {
            return getService().getLocalUserConfig();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the user configuration as {@link String[]} in the same order as in the power menu.
     * Actions are separated with | delimiter.
     * @return {@link String[]}
     */
    public String[] getUserActionsArray() {
        try {
            return getService().getUserActionsArray();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Check if user configuration ({@link List<String>}) contains
     * preference ({@link String})
     * @param preference {@link String}
     * @return {@link boolean}
     */
    public boolean userConfigContains(String preference) {
        try {
            return getService().userConfigContains(preference);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}

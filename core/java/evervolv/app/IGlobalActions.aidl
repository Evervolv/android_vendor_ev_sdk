/*
 * SPDX-FileCopyrightText: 2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package evervolv.app;

/** @hide */
interface IGlobalActions {

    void updateUserConfig(boolean enabled, String action);

    List<String> getLocalUserConfig();

    String[] getUserActionsArray();

    boolean userConfigContains(String preference);
}

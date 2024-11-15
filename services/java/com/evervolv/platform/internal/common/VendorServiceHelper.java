/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.platform.internal.common;

import android.content.Context;

import com.evervolv.platform.internal.VendorService;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Helper methods for fetching a VendorService from a class declaration
 */
public class VendorServiceHelper {
    private final Context mContext;

    public VendorServiceHelper(Context context) {
        mContext = context;
    }

    public VendorService getServiceFor(String className) {
        final Class<VendorService> serviceClass;
        try {
            serviceClass = (Class<VendorService>)Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Failed to create service " + className
                    + ": service class not found", ex);
        }

        return getServiceFromClass(serviceClass);
    }

    public <T extends VendorService> T getServiceFromClass(Class<T> serviceClass) {
        final T service;
        try {
            Constructor<T> constructor = serviceClass.getConstructor(Context.class);
            service = constructor.newInstance(mContext);
        } catch (InstantiationException ex) {
            throw new RuntimeException("Failed to create service " + serviceClass
                    + ": service could not be instantiated", ex);
        } catch (IllegalAccessException | NoSuchMethodException ex) {
            throw new RuntimeException("Failed to create service " + serviceClass
                    + ": service must have a public constructor with a Context argument", ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Failed to create service " + serviceClass
                    + ": service constructor threw an exception", ex);
        }
        return service;
    }
}

# Copyright (C) 2015 The CyanogenMod Project
# Copyright (C) 2017-2018 The LineageOS Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
LOCAL_PATH := $(call my-dir)

evervolv_framework_module := $(LOCAL_INSTALLED_MODULE)

# Make sure that R.java and Manifest.java are built before we build
# the source for this library.
evervolv_framework_res_R_stamp := \
    $(call intermediates-dir-for,APPS,com.evervolv.platform-res,,COMMON)/src/R.stamp
LOCAL_ADDITIONAL_DEPENDENCIES := $(evervolv_framework_res_R_stamp)

$(evervolv_framework_module): | $(dir $(evervolv_framework_module))com.evervolv.platform-res.apk

evervolv_framework_built := $(call java-lib-deps, com.evervolv.platform)

# the sdk as an aar for publish, not built as part of full target
# DO NOT LINK AGAINST THIS IN BUILD
# ============================================================
include $(CLEAR_VARS)

LOCAL_MODULE := com.evervolv.platform.sdk.aar

LOCAL_JACK_ENABLED := disabled

LOCAL_CONSUMER_PROGUARD_FILE := $(LOCAL_PATH)/sdk/proguard.txt

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, sdk/res/res)
LOCAL_MANIFEST_FILE := sdk/AndroidManifest.xml

evervolv_sdk_exclude_files := 'evervolv/library'
LOCAL_JAR_EXCLUDE_PACKAGES := $(evervolv_sdk_exclude_files)
LOCAL_JAR_EXCLUDE_FILES := none

LOCAL_JAVA_LIBRARIES := \
    $(evervolv_sdk_LOCAL_JAVA_LIBRARIES)

LOCAL_STATIC_JAVA_LIBRARIES := com.evervolv.platform.sdk

include $(BUILD_STATIC_JAVA_LIBRARY)
$(LOCAL_MODULE) : $(built_aar)

include $(call first-makefiles-under,$(LOCAL_PATH))

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
evervolv_stub_packages := evervolv.app:evervolv.content:evervolv.hardware:evervolv.os:evervolv.preference:evervolv.provider:evervolv.platform:evervolv.power:evervolv.util:evervolv.style

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

LOCAL_CONSUMER_PROGUARD_FILE := $(LOCAL_PATH)/core/proguard.txt

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, core/res/res)
LOCAL_MANIFEST_FILE := core/AndroidManifest.xml

evervolv_sdk_exclude_files := 'evervolv/library'
LOCAL_JAR_EXCLUDE_PACKAGES := $(evervolv_sdk_exclude_files)
LOCAL_JAR_EXCLUDE_FILES := none

LOCAL_JAVA_LIBRARIES := \
    android-support-annotations \
    android-support-v7-preference \
    android-support-v7-recyclerview \
    android-support-v14-preference

LOCAL_STATIC_JAVA_LIBRARIES := com.evervolv.platform.sdk

include $(BUILD_STATIC_JAVA_LIBRARY)
$(LOCAL_MODULE) : $(built_aar)

# ===========================================================
# Common Droiddoc vars
evervolv_platform_docs_src_files := \
    $(call all-java-files-under, core/java/evervolv) \
    $(call all-html-files-under, core/java/evervolv)

evervolv_platform_docs_java_libraries := \
    androidx.annotation_annotation \
    androidx.legacy_legacy-support-v4 \
    androidx.preference_preference \
    androidx.recyclerview_recyclerview \
    androidx.legacy_legacy-preference-v14 \
    com.evervolv.platform.sdk

# SDK version as defined
evervolv_platform_docs_SDK_VERSION := 9

# release version
evervolv_platform_docs_SDK_REL_ID := 1

evervolv_platform_docs_LOCAL_MODULE_CLASS := JAVA_LIBRARIES

evervolv_platform_docs_LOCAL_DROIDDOC_SOURCE_PATH := \
    $(evervolv_platform_docs_src_files)

evervolv_platform_docs_LOCAL_ADDITIONAL_JAVA_DIR := \
    $(call intermediates-dir-for,JAVA_LIBRARIES,com.evervolv.platform.sdk,,COMMON)

# ====  the api stubs and current.xml ===========================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    $(evervolv_platform_docs_src_files)
LOCAL_INTERMEDIATE_SOURCES:= $(evervolv_platform_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_JAVA_LIBRARIES:= $(evervolv_platform_docs_java_libraries)
LOCAL_MODULE_CLASS:= $(evervolv_platform_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:= $(evervolv_platform_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_ADDITIONAL_JAVA_DIR:= $(evervolv_platform_docs_LOCAL_ADDITIONAL_JAVA_DIR)
LOCAL_ADDITIONAL_DEPENDENCIES:= $(evervolv_platform_docs_LOCAL_ADDITIONAL_DEPENDENCIES)

LOCAL_MODULE := evervolv-api-stubs

LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:= external/doclava/res/assets/templates-sdk

LOCAL_DROIDDOC_STUB_OUT_DIR := $(TARGET_OUT_COMMON_INTERMEDIATES)/JAVA_LIBRARIES/evervolv-sdk_stubs_current_intermediates/src

LOCAL_DROIDDOC_OPTIONS:= \
        -referenceonly \
        -stubpackages $(evervolv_stub_packages) \
        -exclude com.evervolv.platform.internal \
        -api $(INTERNAL_EVERVOLV_PLATFORM_API_FILE) \
        -removedApi $(INTERNAL_EVERVOLV_PLATFORM_REMOVED_API_FILE) \
        -nodocs

LOCAL_UNINSTALLABLE_MODULE := true

include $(BUILD_DROIDDOC)

# $(gen), i.e. framework.aidl, is also needed while building against the current stub.
$(full_target): $(evervolv_framework_built) $(gen)
$(INTERNAL_EVERVOLV_PLATFORM_API_FILE): $(full_target)
$(call dist-for-goals,sdk,$(INTERNAL_EVERVOLV_PLATFORM_API_FILE))


# Documentation
# ===========================================================
include $(CLEAR_VARS)

LOCAL_MODULE := com.evervolv.platform.sdk
LOCAL_INTERMEDIATE_SOURCES:= $(evervolv_platform_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(evervolv_platform_docs_src_files)
LOCAL_ADDITONAL_JAVA_DIR := $(evervolv_platform_docs_LOCAL_ADDITIONAL_JAVA_DIR)

LOCAL_IS_HOST_MODULE := false
LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR := $(SRC_EVERVOLV_DIR)/build/tools/droiddoc/templates
LOCAL_ADDITIONAL_DEPENDENCIES := \
    services \
    com.evervolv.hardware

LOCAL_JAVA_LIBRARIES := $(evervolv_platform_docs_java_libraries)

LOCAL_DROIDDOC_OPTIONS := \
        -android \
        -offlinemode \
        -exclude com.evervolv.platform.internal \
        -hidePackage com.evervolv.platform.internal \
        -hdf android.whichdoc offline \
        -hdf sdk.version $(evervolv_platform_docs_docs_SDK_VERSION) \
        -hdf sdk.rel.id $(evervolv_platform_docs_docs_SDK_REL_ID) \
        -hdf sdk.preview 0 \
        -since $(EVERVOLV_SRC_API_DIR)/1.txt 1

$(full_target): $(evervolv_framework_built) $(gen)
include $(BUILD_DROIDDOC)

include $(call first-makefiles-under,$(LOCAL_PATH))

# Cleanup temp vars
# ===========================================================
evervolv_platform_docs_src_files :=
evervolv_platform_docs_java_libraries :=
evervolv_platform_docs_LOCAL_ADDITIONAL_JAVA_DIR :=

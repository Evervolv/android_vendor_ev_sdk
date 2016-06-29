# Copyright (C) 2013 The CyanogenMod Project
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

include $(CLEAR_VARS)

ifneq ($(BOARD_HARDWARE_CLASS),)
    $(foreach bcp, $(BOARD_HARDWARE_CLASS), \
        $(eval BOARD_SRC_FILES += $(call all-java-files-under, ../../../$(bcp))))
endif

BASE_SRC_FILES += $(call all-java-files-under, src/)

reverse = $(if $(wordlist 2,2,$(1)),$(call reverse,$(wordlist 2,$(words $(1)),$(1))) $(firstword $(1)),$(1))

overriden_classes :=
    $(foreach cf, $(call reverse, $(BOARD_SRC_FILES)), \
        $(eval overriden_classes += $(cf)))

unique_specific_classes :=
    $(foreach cf, $(overriden_classes), \
        $(if $(filter $(notdir $(unique_specific_classes)), $(notdir $(cf))),, \
            $(eval unique_specific_classes += $(cf))))

default_classes :=
    $(foreach cf, $(BASE_SRC_FILES), \
        $(if $(filter $(notdir $(unique_specific_classes)), $(notdir $(cf))),, \
            $(eval default_classes += $(cf))))

LOCAL_SRC_FILES += $(default_classes) $(unique_specific_classes)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := com.evervolv.hardware

include $(BUILD_JAVA_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))


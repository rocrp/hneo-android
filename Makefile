export JAVA_HOME ?= $(shell /usr/libexec/java_home 2>/dev/null)
export ANDROID_HOME ?= $(HOME)/w/Android/sdk

.PHONY: build debug clean install

build:
	./gradlew assembleRelease
	@echo "APK: app/build/outputs/apk/release/app-release-unsigned.apk"

debug:
	./gradlew assembleDebug
	@echo "APK: app/build/outputs/apk/debug/app-debug.apk"

install:
	./gradlew installDebug

clean:
	./gradlew clean

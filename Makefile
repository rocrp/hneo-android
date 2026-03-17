export JAVA_HOME ?= $(shell /usr/libexec/java_home 2>/dev/null)
export ANDROID_HOME ?= $(HOME)/w/Android/sdk

.PHONY: build debug clean install

build:
	./gradlew assembleRelease
	@cp app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/release/hneo.apk
	@echo "APK: app/build/outputs/apk/release/hneo.apk"

debug:
	./gradlew assembleDebug
	@echo "APK: app/build/outputs/apk/debug/app-debug.apk"

install:
	./gradlew installDebug

clean:
	./gradlew clean

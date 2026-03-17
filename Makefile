export JAVA_HOME ?= $(shell /usr/libexec/java_home 2>/dev/null)
export ANDROID_HOME ?= $(HOME)/w/Android/sdk

-include .env
export TELEGRAM_BOT_TOKEN
export TELEGRAM_CHAT_ID

BUILD_NUMBER := $(shell git rev-list --count HEAD)
VERSION_NAME := 0.1.$(BUILD_NUMBER)
APK_NAME := hneo-$(VERSION_NAME).apk
APK_DIR := app/build/outputs/apk/release

.PHONY: build debug clean install beta

build:
	./gradlew assembleRelease
	@cp $(APK_DIR)/app-release.apk $(APK_DIR)/$(APK_NAME)
	@echo "APK: $(APK_DIR)/$(APK_NAME)"

debug:
	./gradlew assembleDebug
	@echo "APK: app/build/outputs/apk/debug/app-debug.apk"

install:
	./gradlew installDebug

clean:
	./gradlew clean

beta: build
	@# Tag this build (skip if tag already exists on HEAD)
	@if git rev-parse "build-$(BUILD_NUMBER)" >/dev/null 2>&1; then \
		echo "Tag build-$(BUILD_NUMBER) already exists, skipping"; \
	else \
		git tag -a "build-$(BUILD_NUMBER)" -m "Build $(BUILD_NUMBER)"; \
		echo "Tagged build-$(BUILD_NUMBER)"; \
	fi
	@# Build changelog and send to Telegram
	@LAST_TAG=$$(git describe --tags --match "build-*" --abbrev=0 HEAD~ 2>/dev/null); \
	if [ -n "$$LAST_TAG" ]; then \
		CHANGELOG=$$(git log $$LAST_TAG..HEAD --pretty=format:'- %s'); \
	else \
		CHANGELOG=$$(git log -n 5 --pretty=format:'- %s'); \
	fi; \
	CAPTION=$$(printf "hneo Android v$(VERSION_NAME)\n\n%s" "$$CHANGELOG"); \
	echo "=== $$CAPTION ==="; \
	curl -s -X POST "https://api.telegram.org/bot$(TELEGRAM_BOT_TOKEN)/sendDocument" \
		-F chat_id="$(TELEGRAM_CHAT_ID)" \
		-F document=@"$(APK_DIR)/$(APK_NAME)" \
		-F caption="$$CAPTION" \
		| python3 -c "import sys,json; r=json.load(sys.stdin); print('Sent!' if r.get('ok') else f'Failed: {r}')"

.PHONY: all release install clean apk android linux win32

all: android javadoc linux win32

release: apk win32

install: android
	ant release install

icon: icon.svg
	inkscape -z -e res/drawable-xxxhdpi/icon.png -w 192 -h 192 icon.svg
	inkscape -z -e res/drawable-xxhdpi/icon.png -w 144 -h 144 icon.svg
	inkscape -z -e res/drawable-xhdpi/icon.png -w 96 -h 96 icon.svg
	inkscape -z -e res/drawable-hdpi/icon.png -w 72 -h 72 icon.svg
	inkscape -z -e res/drawable-mdpi/icon.png -w 48 -h 48 icon.svg
	inkscape -z -e res/drawable-xxxhdpi/status.png -w 96 -h 96 icon.svg
	inkscape -z -e res/drawable-xxhdpi/status.png -w 72 -h 72 icon.svg
	inkscape -z -e res/drawable-xhdpi/status.png -w 48 -h 48 icon.svg
	inkscape -z -e res/drawable-hdpi/status.png -w 36 -h 38 icon.svg
	inkscape -z -e res/drawable-mdpi/status.png -w 24 -h 24 icon.svg

apk: android
	ant release

javadoc:
	ant javadoc
	@echo "convert javadoc by opencc:"
	@find docs -type f -name *.html| xargs -i opencc -i {} -o {}

android:
	mkdir -p build-android
	(cd build-android; cmake \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_TOOLCHAIN_FILE=../android-cmake/android.toolchain.cmake \
		-DLIBRARY_OUTPUT_PATH_ROOT=.. \
		-DANDROID_TOOLCHAIN_NAME=arm-linux-androideabi-4.9 \
		-DANDROID_ABI=armeabi \
		-DANDROID_STL=c++_shared \
		-DANDROID_NATIVE_API_LEVEL=9 ../jni)
	${MAKE} -C build-android rime_jni

linux:
	mkdir -p build-linux
	(cd build-linux; cmake -DCMAKE_BUILD_TYPE=Release ../jni)
	${MAKE} -C build-linux

win32:
	mkdir -p build-win32
	(cd build-win32; i686-w64-mingw32-cmake -DCMAKE_BUILD_TYPE=Release ../jni)
	${MAKE} -C build-win32 rime
	mkdir -p bin
	(cd build-win32; 7z a ../bin/rime-win32-`date +%Y%m%d`.dll.7z rime.dll)

clean:
	git clean -fd
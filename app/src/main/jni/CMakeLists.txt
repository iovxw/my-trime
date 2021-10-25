cmake_minimum_required (VERSION 3.18.0)
project(jni)

set(ANDROID_STL c++_shared)
set(CMAKE_CXX_STANDARD 14)

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

include(Iconv)
# workaround for boost install
install(TARGETS iconv EXPORT boost_locale-targets)

add_subdirectory(boost)

include(Capnproto)

#add_subdirectory(glog)

add_subdirectory(yaml-cpp)

option(LEVELDB_BUILD_TESTS "" OFF)
option(LEVELDB_BUILD_BENCHMARKS "" OFF)
option(LEVELDB_INSTALL "" OFF)
add_subdirectory(leveldb)

add_subdirectory(OpenCC)
# install opencc headers manually
file(GLOB LIBOPENCC_HEADERS
  OpenCC/src/*.hpp
  OpenCC/src/*.h
  "${CMAKE_BINARY_DIR}/OpenCC/src/opencc_config.h"
)
make_directory("${CMAKE_BINARY_DIR}/include/opencc")
foreach(header ${LIBOPENCC_HEADERS})
  configure_file(${header} "${CMAKE_BINARY_DIR}/include/opencc" COPYONLY)
endforeach()

option(BUILD_TEST "" OFF)
option(ENABLE_LOGGING "" OFF)
include_directories("librime/thirdparty/include") # for X11/keysym.h
include_directories("capnproto/c++/src")
# workaround for librime/tools/rime_api_console.cc
# TODO: fix it in upstream
string(REPLACE "-Werror=format-security" "" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})
string(REPLACE "-Wformat" "" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})
add_definitions("-Wno-format")
# workaround end
add_subdirectory(librime)

# TODO: PLUGINS

# rime_jni
if(UNIX)
find_package(Opencc REQUIRED)
file(GLOB RIME_JNI_SOURCES librime_jni/*.cc)
add_library(rime_jni SHARED ${RIME_JNI_SOURCES})
target_include_directories(rime_jni PRIVATE
  "librime/src"
  "${Opencc_INCLUDE_PATH}"
)
target_link_libraries(rime_jni rime libopencc)

string(TIMESTAMP TODAY "%Y%m%d")

target_compile_definitions(rime_jni PRIVATE
  CLASSNAME="com/osfans/trime/Rime"
  TRIME_VERSION="0"
  OPENCC_VERSION="0"
  LIBRIME_VERSION="0"
)

if(ANDROID)
  target_link_libraries(rime_jni log)
else(ANDROID)
  target_include_directories(rime_jni BEFORE PRIVATE
    /usr/lib/jvm/default/include
    /usr/lib/jvm/default/include/linux
  )
endif(ANDROID)
endif(UNIX)

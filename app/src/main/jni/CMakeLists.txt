cmake_minimum_required (VERSION 3.18.0)
project(jni)

set(ANDROID_STL c++_shared)
set(CMAKE_CXX_STANDARD 14)

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

set(ICONV_SOURCES
  libiconv/libcharset/lib/localcharset.c
  libiconv/lib/iconv.c
  libiconv/lib/relocatable.c
)
add_library(iconv STATIC ${ICONV_SOURCES})
add_library(Iconv::Iconv ALIAS iconv)
target_compile_definitions(iconv PRIVATE
  LIBDIR="c" BUILDING_LIBICONV IN_LIBRARY
)
target_include_directories(iconv BEFORE PRIVATE
  "libiconv/lib"
  "libiconv/include"
  "libiconv/libcharset/include"
)
install(TARGETS iconv EXPORT boost_locale-targets)
include_directories(
  "libiconv/include"
)

#file(GLOB BOOST_SOURCES
#  boost/libs/filesystem/src/*.cpp
#  boost/libs/regex/src/*.cpp
#  boost/libs/system/src/error_code.cpp
#  boost/libs/locale/src/encoding/codepage.cpp
#)
#set(RIME_BOOST_SIGNALS2 1)
#add_library(boost STATIC ${BOOST_SOURCES} ${BOOST_SIGNAL_SOURCES})
#target_include_directories(boost PRIVATE ${CMAKE_BINARY_DIR}/include)
#target_compile_definitions(boost PRIVATE BOOST_LOCALE_WITH_ICONV)
#target_link_libraries(boost iconv)
set(BOOST_INCLUDE_LIBRARIES "filesystem" "atomic" "regex" "system" "locale")
foreach(comp ${BOOST_INCLUDE_LIBRARIES})
  LIST(APPEND Boost_LIBRARIES Boost::${comp})
endforeach()
add_subdirectory(boost)

file(GLOB_RECURSE BOOST_HEADERS boost/libs/*.ipp boost/libs/*.h boost/libs/*.hpp)
foreach(head ${BOOST_HEADERS})
  string(REGEX REPLACE "^.*boost/libs" "boost/libs" head ${head})
  string(REGEX REPLACE "^.*include/boost/" "" headout ${head})
  configure_file(
    ${CMAKE_CURRENT_LIST_DIR}/${head}
    ${CMAKE_BINARY_DIR}/include/boost/${headout} COPYONLY
  )
endforeach()

option(BUILD_TESTING "" OFF)
option(EXTERNAL_CAPNP "" ON)
find_package(CapnProto CONFIG QUIET)
if(NOT CapnProto_FOUND)
  # No working installation of Cap'n Proto found, so fall back to searching the environment.
  #
  # We search for the external capnp compiler binaries via $CAPNP, $CAPNPC_CXX, and
  # find_program(). find_program() will use various paths in its search, among them
  # ${CMAKE_PREFIX_PATH}/bin and $PATH.

  if(NOT CAPNP_EXECUTABLE)
    if(DEFINED ENV{CAPNP})
      set(CAPNP_EXECUTABLE "$ENV{CAPNP}")
    else()
      find_program(CAPNP_EXECUTABLE capnp)
    endif()
  endif()

  if(NOT CAPNPC_CXX_EXECUTABLE)
    if(DEFINED ENV{CAPNPC_CXX})
      set(CAPNPC_CXX_EXECUTABLE "$ENV{CAPNPC_CXX}")
    else()
      # Also search in the same directory that `capnp` was found in
      get_filename_component(capnp_dir "${CAPNP_EXECUTABLE}" DIRECTORY)
      find_program(CAPNPC_CXX_EXECUTABLE capnpc-c++ HINTS "${capnp_dir}")
    endif()
  endif()
endif()
add_subdirectory(capnproto)
configure_file(
  capnproto/c++/cmake/CapnProtoTargets.cmake
  ${CMAKE_CURRENT_BINARY_DIR}/capnproto/c++/cmake COPYONLY
)
set(CapnProto_DIR "${CMAKE_CURRENT_BINARY_DIR}/capnproto/c++/cmake")

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

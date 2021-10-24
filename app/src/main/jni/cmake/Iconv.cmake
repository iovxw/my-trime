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
target_include_directories(iconv PRIVATE
  "libiconv/lib"
  "libiconv/libcharset/include"
)

# We can't add it to target PUBLIC include since it's prefixed with source dir
include_directories("libiconv/include")

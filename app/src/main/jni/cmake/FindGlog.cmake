set(Glog_FOUND TRUE)
set(PKG_CONFIG_VERSION_STRING 0.0.0)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(Glog
    FOUND_VAR
        Glog_FOUND
    REQUIRED_VARS
        PKG_CONFIG_VERSION_STRING
)

#mark_as_advanced(PkgConfig_FOUND PKG_CONFIG_VERSION_STRING)

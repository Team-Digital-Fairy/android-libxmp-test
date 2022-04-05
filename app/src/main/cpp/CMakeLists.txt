
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# Declares and names the project.

project("jni_shared_test")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             jni_shared_test

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             jni_shared_test.cpp )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )


find_library(
        opensl-lib
        OpenSLES
)
# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

set( lib_src_DIR ../libxmp )
set( lib_build_DIR ../libxmp/outputs )
file(MAKE_DIRECTORY ${lib_build_DIR})
set(BUILD_SHARED OFF)
set(BUILD_STATIC ON)

add_subdirectory(
        ${lib_src_DIR}
        ${lib_build_DIR}
)

#add_library(xmp INTERFACE)
#target_link_libraries(xmp INTERFACE xmp_static)

# ....
target_link_libraries( # Specifies the target library.
        jni_shared_test
        # Links the target library to the log library
        # included in the NDK.

        XMP_IF # ORDER is important, if xmp depends on lib, put it BEFORE log
        ${log-lib}
        ${opensl-lib}
        )


#add_library(libxmp STATIC IMPORTED)
#set_target_properties(libxmp PROPERTIES IMPORTED_LOCATION
#        ${lib_build_DIR}/${ANDROID_ABI}/libxmp.a)
#include_directories( ${lib_src_DIR}/include )
#target_link_libraries( native-jni_shared_test  libxmp )


#target_link_libraries( # Specifies the target library.
#                       jni_shared_test
                       # Links the target library to the log library
                       # included in the NDK.
#                       ${log-lib}
#                        libxmp
#        )


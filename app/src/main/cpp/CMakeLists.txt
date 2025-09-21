cmake_minimum_required(VERSION 3.10.2)
project("spm_jni")

add_library(sentencepiece_jni SHARED spm_jni.cpp)

# libsentencepiece.so 경로 연결
add_library(sentencepiece SHARED IMPORTED)
set_target_properties(sentencepiece PROPERTIES
        IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../../sentencepiece/build-android/src/libsentencepiece.so
)

find_library(log-lib log)

target_include_directories(sentencepiece_jni PRIVATE
        ${CMAKE_SOURCE_DIR}/../../../../sentencepiece/src
)

target_link_libraries(sentencepiece_jni
        sentencepiece
        ${log-lib})

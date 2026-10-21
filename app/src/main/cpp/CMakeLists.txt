cmake_minimum_required(VERSION 3.22.1)
project(replicant303_engine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(replicant303_engine SHARED
    jni_bridge.cpp
    engine/audio_driver.cpp
    engine/engine.cpp
    engine/sequencer.cpp
    engine/preset.cpp
    engine/oscillator.cpp
    engine/filter.cpp
    engine/distortion.cpp
)

target_include_directories(replicant303_engine PRIVATE engine)

target_compile_options(replicant303_engine PRIVATE
    -O3
    -fno-math-errno
    -fno-trapping-math
    -Wall
    -Wextra
    -Wno-unused-parameter
)

target_link_libraries(replicant303_engine PRIVATE
    oboe::oboe
    android
    log
)

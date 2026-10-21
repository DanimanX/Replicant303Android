cmake_minimum_required(VERSION 3.22)
project(replicant303_host LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_BUILD_TYPE Release)

set(ENGINE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp)

add_library(r303_core STATIC
    ${ENGINE_DIR}/engine/engine.cpp
    ${ENGINE_DIR}/engine/sequencer.cpp
    ${ENGINE_DIR}/engine/preset.cpp
    ${ENGINE_DIR}/engine/oscillator.cpp
    ${ENGINE_DIR}/engine/filter.cpp
    ${ENGINE_DIR}/engine/distortion.cpp
)
target_include_directories(r303_core PUBLIC ${ENGINE_DIR})
target_compile_options(r303_core PRIVATE -O3 -Wall -Wextra -Wno-unused-parameter)

add_executable(r303_tests tests.cpp)
target_link_libraries(r303_tests PRIVATE r303_core)

add_executable(render_demo render_demo.cpp)
target_link_libraries(render_demo PRIVATE r303_core)

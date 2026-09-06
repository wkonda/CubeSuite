cmake_minimum_required(VERSION 3.22)
project("tuner")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(tuner_engine SHARED
        src/main/cpp/native-lib.cpp
        src/main/cpp/tuner_engine.cpp
        src/main/cpp/filter.cpp
        src/main/cpp/pitch_detector.cpp
)

target_compile_options(tuner_engine PRIVATE -O3 -DNDEBUG)
target_include_directories(tuner_engine PRIVATE src/main/cpp)

find_library(log-lib log)
target_link_libraries(tuner_engine ${log-lib})

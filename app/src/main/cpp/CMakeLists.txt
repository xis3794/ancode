cmake_minimum_required(VERSION 3.22.1)
project(ancode_pty C)

add_library(pty_shim SHARED pty_shim.c)
target_link_libraries(pty_shim android log)

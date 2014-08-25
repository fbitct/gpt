#!/usr/bin/python
from buildutil import *

###########
# TARGETS #
###########

def clean():
    rmFolder("gpt-common/src_generated")
    rmFolder("gpt-displaystransmitter/target")
    check_call("sbt clean", shell=True)
    
def build():
    generate_model()
    build_cpp()
    check_call("sbt compile package publish-local assembly", shell=True)

def test():
    sbt_test(".")

def eclipse():
    check_call("sbt eclipse", shell=True)

def publish():
    print("publish(): Not yet implemented!")
    
def generate():
    generate_model()

def release(folder):
    rmFolder(folder)
    mkFolder(folder)
    for file in findFiles(".", '*assembly*.jar*'):
        trgFilePath = folder + "/" + os.path.basename(file)
        shutil.copyfile(file, trgFilePath)
    for file in findFiles(".", '*.json*'):
        trgFilePath = folder + "/" + os.path.basename(file)
        shutil.copyfile(file, trgFilePath)
    shutil.copyfile("gpt-displaystransmitter/target/loader/Release/gpt_displaystransmitter_loader.exe", "release/gpt_displaystransmitter_loader.exe")
    shutil.copyfile("gpt-displaystransmitter/target/hook/Release/gpt_displaystransmitter_hook.dll", "release/gpt_displaystransmitter_hook.dll")

    
###########
# HELPERS #
###########

mgen_cmd = "NEEDS_TO_BE_SET"
pluginPaths = "NEEDS_TO_BE_SET"
default_cpp_build_cfg = "NEEDS_TO_BE_SET"

def generate_model():
    check_call("mgen models/project.xml", cwd="gpt-common", shell=True)
    
def build_cpp():
    mkFolder("gpt-displaystransmitter/target")
    mkFolder("gpt-displaystransmitter/target/hook")
    mkFolder("gpt-displaystransmitter/target/loader") 
    cmake("gpt-displaystransmitter/target/hook", "../../src/hook -T v120_xp", "Release")
    cmake("gpt-displaystransmitter/target/loader", "../../src/loader -T v120_xp", "Release")
    cppBuild("gpt-displaystransmitter/target/hook", "Release", "gpt_displaystransmitter_hook")
    cppBuild("gpt-displaystransmitter/target/loader", "Release", "gpt_displaystransmitter_loader")

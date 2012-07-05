ifeq ($(origin JAVA_HOME), undefined)
  JAVA_HOME=/usr
endif

ifeq ($(origin NETLOGO), undefined)
  NETLOGO=../..
endif

ARCH=32
OS=invalid

ifneq (,$(findstring CYGWIN,$(shell uname -s)))

  COLON=\;
  JAVA_HOME := `cygpath -up "$(JAVA_HOME)"`
  OS=windows

  ifneq (,$(findstring WOW64,$(shell uname -s)))
    ARCH=64
  endif

else

  COLON=:

  # This OS/arch gymnastics can go away when this transitions to SBT
  ifneq (,$(findstring Darwin,$(shell uname)))
    OS=macosx
  endif

  ifneq (,$(findstring 64,$(shell uname -m)))
    ARCH=64
  endif

endif

EXT_NAME=yoshi
JAR_NAME=$(EXT_NAME).jar
PACK_NAME=$(JAR_NAME).pack.gz

GST_NAME=gstreamer-java-1.5
GST_JAR=$(GST_NAME).jar
GST_PACK=$(GST_JAR).pack.gz

JNA_NAME=jna
JNA_JAR=$(JNA_NAME).jar
JNA_PACK=$(JNA_JAR).pack.gz

LIB_EXT=gstreamer/
LIB_DIR=lib/
LIB_TYPE=$(OS)$(ARCH)
LIB_FILE=$(LIB_TYPE).tar

JAR_REPO=http://ccl.northwestern.edu/devel/

SRCS=$(shell find src/ -type f -name '*.java')

$(JAR_NAME).jar $(PACK_NAME): $(SRCS) $(GST_JAR) $(GST_PACK) $(JNA_JAR) $(JNA_PACK) $(LIB_DIR)$(LIB_TYPE) manifest.txt
	mkdir -p classes
	$(JAVA_HOME)/bin/javac -g -encoding us-ascii -source 1.5 -target 1.5 -classpath $(NETLOGO)/NetLogoLite.jar$(COLON)$(GST_JAR)$(COLON)$(JNA_JAR)$(COLON)gst-video.jar -d classes $(SRCS)
	jar cmf manifest.txt $(JAR_NAME) -C classes .
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip $(PACK_NAME) $(JAR_NAME)

$(LIB_DIR)$(LIB_TYPE):
	mkdir -p lib
	curl -f -s -S $(JAR_REPO)$(LIB_EXT)$(LIB_FILE) -o $(LIB_DIR)$(LIB_FILE)
	tar -C $(LIB_DIR) -xvzf $(LIB_DIR)$(LIB_FILE)
	rm $(LIB_DIR)$(LIB_FILE)

$(GST_JAR) $(GST_PACK):
	curl -f -s -S $(JAR_REPO)$(GST_JAR) -o $(GST_JAR)
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip $(GST_PACK) $(GST_JAR)

$(JNA_JAR) $(JNA_PACK):
	curl -f -s -S $(JAR_REPO)$(JNA_JAR) -o $(JNA_JAR)
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip $(JNA_PACK) $(JNA_JAR)

$(EXT_NAME).zip: $(JAR_NAME)
	rm -rf $(EXT_NAME)
	mkdir $(EXT_NAME)
	cp -rp $(JAR_NAME) $(PACK_NAME) README.md Makefile src manifest.txt $(EXT_NAME)
	zip -rv $(EXT_NAME).zip $(EXT_NAME)
	rm -rf $(EXT_NAME)

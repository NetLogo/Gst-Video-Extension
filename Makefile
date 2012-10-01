ifeq ($(origin JAVA_HOME), undefined)
  JAVA_HOME=/usr
endif

ifeq ($(origin SCALA_HOME), undefined)
  SCALA_HOME=../..
endif

ifeq ($(origin NETLOGO), undefined)
  NETLOGO=../..
endif

OS=invalid

ifneq (,$(findstring CYGWIN,$(shell uname -s)))
  COLON=\;
  JAVA_HOME := `cygpath -up "$(JAVA_HOME)"`
  SCALA_HOME := `cygpath -up "$(SCALA_HOME)"`
  OS=windows
else

  COLON=:

  # This OS gymnastics can go away when this transitions to SBT
  ifneq (,$(findstring Darwin,$(shell uname)))
    OS=macosx
  endif

endif

EXT_NAME=gst-video
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
LIB_32=$(OS)32
LIB_64=$(OS)64
LIB_FILE_32=$(LIB_32).tar
LIB_FILE_64=$(LIB_64).tar

JAR_REPO=http://ccl.northwestern.edu/devel/

PROCESSING_NAME=processing-lib
PROCESSING_JAR=$(PROCESSING_NAME).jar
PROCESSING_PACK=$(PROCESSING_JAR).pack.gz
PROCESSING_DIR=processing-lib
PROCESSING_CLASSES=$(PROCESSING_DIR)/classes
PROCESSING_SRCS=$(shell find $(PROCESSING_DIR)/src -type f -name '*.java')

SRCS=$(shell find src -type f -name '*.scala')

$(JAR_NAME).jar $(PACK_NAME): $(SRCS) $(GST_JAR) $(GST_PACK) $(JNA_JAR) $(JNA_PACK) $(PROCESSING_JAR) $(PROCESSING_PACK) $(LIB_DIR)$(LIB_32) $(LIB_DIR)$(LIB_64) manifest.txt
	mkdir -p classes
	$(SCALA_HOME)/bin/scalac -deprecation -unchecked -encoding us-ascii -classpath $(NETLOGO)/NetLogoLite.jar$(COLON)$(GST_JAR)$(COLON)$(JNA_JAR)$(COLON)$(PROCESSING_JAR)$(COLON)classes -d classes $(SRCS)
	jar cmf manifest.txt $(JAR_NAME) -C classes .
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip $(PACK_NAME) $(JAR_NAME)

clean:
	rm -rf classes
	rm *.jar *.jar.pack.gz

$(LIB_DIR)$(LIB_32) $(LIB_DIR)$(LIB_64):
	mkdir -p lib
	curl -f -s -S $(JAR_REPO)$(LIB_EXT)$(LIB_FILE_32) -o $(LIB_DIR)$(LIB_FILE_32)
	curl -f -s -S $(JAR_REPO)$(LIB_EXT)$(LIB_FILE_64) -o $(LIB_DIR)$(LIB_FILE_64)
	tar -C $(LIB_DIR) -xvzf $(LIB_DIR)$(LIB_FILE_32)
	tar -C $(LIB_DIR) -xvzf $(LIB_DIR)$(LIB_FILE_64)
	rm $(LIB_DIR)$(LIB_FILE_32) $(LIB_DIR)$(LIB_FILE_64)

$(GST_JAR) $(GST_PACK):
	curl -f -s -S $(JAR_REPO)$(GST_JAR) -o $(GST_JAR)
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip $(GST_PACK) $(GST_JAR)

$(JNA_JAR) $(JNA_PACK):
	curl -f -s -S $(JAR_REPO)$(JNA_JAR) -o $(JNA_JAR)
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip $(JNA_PACK) $(JNA_JAR)

$(PROCESSING_JAR) $(PROCESSING_PACK):
	mkdir -p $(PROCESSING_CLASSES)
	$(JAVA_HOME)/bin/javac -g -encoding us-ascii -source 1.5 -target 1.5 -classpath $(GST_JAR)$(COLON)$(JNA_JAR) -d $(PROCESSING_CLASSES) $(PROCESSING_SRCS)
	jar cf $(PROCESSING_JAR) -C $(PROCESSING_CLASSES) .
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip $(PROCESSING_PACK) $(PROCESSING_JAR)

$(EXT_NAME).zip: $(JAR_NAME)
	rm -rf $(EXT_NAME)
	mkdir $(EXT_NAME)
	cp -rp $(JAR_NAME) $(PACK_NAME) README.md Makefile src manifest.txt $(EXT_NAME)
	zip -rv $(EXT_NAME).zip $(EXT_NAME)
	rm -rf $(EXT_NAME)

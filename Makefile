ifeq ($(origin JAVA_HOME), undefined)
  JAVA_HOME=/usr
endif

ifeq ($(origin NETLOGO), undefined)
  NETLOGO=../..
endif

ifneq (,$(findstring CYGWIN,$(shell uname -s)))
  COLON=\;
  JAVA_HOME := `cygpath -up "$(JAVA_HOME)"`
else
  COLON=:
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

JAR_REPO=http://ccl.northwestern.edu/devel/

SRCS=$(wildcard src/*.java)

$(JAR_NAME).jar $(PACK_NAME): $(SRCS) $(GST_JAR) $(GST_PACK) $(JNA_JAR) $(JNA_PACK) manifest.txt
	mkdir -p classes
	$(JAVA_HOME)/bin/javac -g -encoding us-ascii -source 1.5 -target 1.5 -classpath $(NETLOGO)/NetLogoLite.jar$(COLON)$(GST_JAR)$(COLON)$(JNA_JAR) -d classes $(SRCS)
	jar cmf manifest.txt $(JAR_NAME) -C classes .
	pack200 --modification-time=latest --effort=9 --strip-debug --no-keep-file-order --unknown-attribute=strip $(PACK_NAME) $(JAR_NAME)

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

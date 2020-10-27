.PHONY: build clean all
.DEFAULT_GOAL: all

all: clean build

build:
	elm make src/Main.elm --output build/ui/app.js
	cp index.html build/ui/index.html

clean:
	rm -f build/ui/*
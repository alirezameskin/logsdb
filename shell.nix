let
  # Override the java version of sbt package
  config = {
    packageOverrides = pkgs: rec {
      sbt = pkgs.sbt.overrideAttrs (
        old: rec {
          version = "1.3.13";

          patchPhase = ''
            echo -java-home ${pkgs.openjdk11} >> conf/sbtopts
          '';
        }
      );
    };
  };

  pkgs = import <nixpkgs> { inherit config; };
in
  pkgs.mkShell {
    buildInputs = with pkgs; [
      openjdk11
      sbt
      elmPackages.elm
      gnumake
    ];
  }
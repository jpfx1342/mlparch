MLPArch
=======

MLPArch is an archive tool for the My Little Pony mobile game by Gameloft. It
can open and create the MLPA format (also known as the .obb format), as well
as help with patching the game's XML files.

More information is available on the [MLP Mod wiki](http://mlp-mod.wikia.com/wiki/MLPArch).

### How to Use ###
If all you want to do is extract the files from the game, then navigate to the
folder with your .obb, and issue this command in a command prompt:

    java -jar path/to/MLPArch.jar mlpa -a nameofyour.obb

All files will be extracted to a folder named "extract"

To extract only certain files, such as all xml files:

    java -jar path/to/MLPArch.jar mlpa -a nameofyour.obb -r .*/.xml

(The weird escaping is because it's a regex expression.)

To apply the default patch:

    java -jar path/to/MLPArch.jar xmlp

To build a new .obb, named "newarch.obb":

    java -jar path/to/MLPArch.jar mlpa -p -a newarch.obb


### Help ###
MLPArch actually contains two sub programs, and which program is used is based
on the first argument given to the program. Right now, the subprograms are:

    mlpa (MLPArch)
    xmlp (XMLPatch)

You can get more information about a specific program by passing it the --help
or -? argument. For example, to show the MLPArch help:

    java -jar path/to/MLPArch.jar mlpa --help
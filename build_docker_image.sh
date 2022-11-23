# DOC
#
# This script builds docker image of Metabase based on Ubuntu. DuckDB pluging is included.
#
# The docker/metabase/bin/docker folder contains a bit modified files from the Metabase source.
#

# Common
CUR=$PWD

# Metabase
MB_VSN=v1.44.6
MB_GIT_URL=https://github.com/metabase/metabase.git
MB_SRC_FOLDER=docker/metabase/source
MB_IMAGE_NAME=ubuntu_metabase
MB_DUCKDB_IMAGE_NAME=metabase_duckdb

# Clone metabase source code
if [ ! -d "$MB_SRC_FOLDER/$MB_VSN" ] ; then
  git clone --depth 1 --branch $MB_VSN $MB_GIT_URL $MB_SRC_FOLDER/$MB_VSN
fi

# Copy ubuntu based docker files/sh script into source code of Metabase
yes | cp -rf docker/metabase/Dockerfile $MB_SRC_FOLDER/$MB_VSN
yes | cp -rf docker/metabase/bin/docker/* $MB_SRC_FOLDER/$MB_VSN/bin/docker

# Build the Metabase Ubuntu based docker image
cd $MB_SRC_FOLDER/$MB_VSN && docker build -t $MB_IMAGE_NAME -f Dockerfile . && cd $CUR

# Build the Metabase image with DuckDB plugin
docker build -t $MB_DUCKDB_IMAGE_NAME -f docker/Dockerfile .

# Metabase soure code
MB_SRC_FOLDER=docker/metabase/source
MB_GIT_URL=https://github.com/metabase/metabase.git
MB_IMAGE_NAME=ubuntu_metabase
MB_DUCKDB_IMAGE_NAME=metabase_duckdb

# Clone metabase source code
if [ ! -d "$MB_SRC_FOLDER" ] ; then
  git clone $MB_GIT_URL $MB_SRC_FOLDER
else
  git -C $MB_SRC_FOLDER fetch
  git -C $MB_SRC_FOLDER reset --hard HEAD
  git -C $MB_SRC_FOLDER merge origin/master
fi

# Copy ubuntu based docker files/sh script into source code of Metabase
yes | cp -rf docker/metabase/Dockerfile $MB_SRC_FOLDER
yes | cp -rf docker/metabase/bin/docker/* $MB_SRC_FOLDER/bin/docker

# Build the Metabase Ubuntu based docker image
docker build -t $MB_IMAGE_NAME -f $MB_SRC_FOLDER/Dockerfile .

# Build the Metabase image with DuckDB plugin
docker build -t $MB_DUCKDB_IMAGE_NAME -f docker/Dockerfile .

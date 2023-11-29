FROM tomcat:9-jdk21-openjdk-slim

# - mount /etc/dsg/, /var/log/tomcat, /var/log/dsg outside of container, maybe /var/lib/dsg

RUN mkdir -p /usr/local/tomcat/webapps/ROOT
# copy the pages
COPY dsg_src/httpdocs/ /usr/local/tomcat/webapps/ROOT/
# copy the libs
COPY dsg_src/lib/* /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/
RUN mkdir -p /usr/local/tomcat/webapps/tmp_src
# copy the code
COPY dsg_src/java /usr/local/tomcat/webapps/tmp_src
COPY build-docker.xml /usr/local/tomcat/webapps/
# clean the code
ARG ENV=""
# set to linux/amd64 for deployment
ARG DOCKER_DEFAULT_PLATFORM
RUN mkdir -p /var/lib/dsg/gameServer/game && \
  mkdir -p /var/lib/dsg/gameServer/player && \
  apt update && apt install nala -y && \
  # fish and bob
  nala install -y curl fish git && \
  curl -L https://get.oh-my.fish > install && \
  fish install --noninteractive && \
  fish -c "omf install bobthefish" && \
  mkdir -p ~/.config/fish/functions && \
  echo "function l\n  ls -Alh \$argv\nend" > ~/.config/fish/functions/l.fish && \
  rm install && \
  # AstroNvim for Apple Silicon
  if [ "$DOCKER_DEFAULT_PLATFORM" = "" ]; then \
      curl -LO https://github.com/matsuu/neovim-aarch64-appimage/releases/download/v0.9.0/nvim-v0.9.0-aarch64.appimage; \
      chmod u+x nvim-v0.9.0-aarch64.appimage; \
      ./nvim-v0.9.0-aarch64.appimage --appimage-extract; \
      cp -r squashfs-root/* /; \
      rm -rf squashfs-root nvim-v0.9.0-aarch64.appimage; \
  fi && \
  # AstroNvim for linux/amd64
  if [ "$DOCKER_DEFAULT_PLATFORM" = "linux/amd64" ]; then  \
    curl -LO https://github.com/neovim/neovim/releases/download/v0.9.0/nvim-linux64.tar.gz; \
    tar xzf nvim-linux64.tar.gz; \
    cp -r nvim-linux64/* /usr; \
    rm -rf nvim-linux64 nvim-linux64.tar.gz; \
  fi && \
  git clone --depth 1 https://github.com/AstroNvim/AstroNvim ~/.config/nvim && \
  nvim --headless +PlugInstall +qall && \
  nala install ant -y && \
  rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/opengl/ && \
  rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/gameServer/tourney/test/ && \
  rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/message/test/ && \
  rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/turnBased/test/ && \
# compile the code
  ant -f /usr/local/tomcat/webapps/build-docker.xml && \
# cleanup
  rm -rf /usr/local/tomcat/webapps/tmp_src && \
  rm /usr/local/tomcat/webapps/build-docker.xml && \
  nala remove -y ant && nala autoremove -y && nala autopurge -y && apt remove nala -y && apt autoremove -y && apt autopurge -y && \
# local context doesn't access remote instance
  mv /usr/local/tomcat/webapps/ROOT/META-INF/${ENV}context.xml /usr/local/tomcat/webapps/ROOT/META-INF/context.xml

# copy the other domains
COPY submanifolddomains/ /usr/local/tomcat/

# copy the react components (make sure they're built)
COPY ../react-live-game-room/build /usr/local/tomcat/webapps/ROOT/gameServer/live
COPY ../react-mmai/build /usr/local/tomcat/webapps/ROOT/gameServer/mmai

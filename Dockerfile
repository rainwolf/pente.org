FROM tomcat:9-jdk21-openjdk-slim

# - mount /etc/dsg/, /var/log/tomcat, /var/log/dsg outside of container, maybe /var/lib/dsg

RUN mkdir -p /usr/local/tomcat/webapps/ROOT && mkdir -p /usr/local/tomcat/webapps/tmp_src
# copy the pages
COPY dsg_src/httpdocs/ /usr/local/tomcat/webapps/ROOT/
# copy the libs
COPY dsg_src/lib/* /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/
# copy the code
COPY deploy /usr/local/tomcat/webapps/tmp_src
COPY build-docker.xml /usr/local/tomcat/webapps/
ARG ENV=""
# set to linux/amd64 for deployment
ARG DOCKER_DEFAULT_PLATFORM
RUN mkdir -p /var/lib/dsg/gameServer/game && \
  mkdir -p /var/lib/dsg/gameServer/player && \
  apt update && \
  apt install nala -y && \
  nala install -y curl fish git && \
  # AstroNvim for Apple Silicon
  if [ "$DOCKER_DEFAULT_PLATFORM" = "" ]; then \
      curl -LO https://github.com/matsuu/neovim-aarch64-appimage/releases/download/v0.9.4/nvim-v0.9.4-aarch64.appimage; \
      chmod u+x nvim-v0.9.4-aarch64.appimage; \
      ./nvim-v0.9.4-aarch64.appimage --appimage-extract; \
      cp -r squashfs-root/* /; \
      rm -rf squashfs-root nvim-v0.9.4-aarch64.appimage; \
  fi && \
  # AstroNvim for linux/amd64
  if [ "$DOCKER_DEFAULT_PLATFORM" = "linux/amd64" ]; then \
    curl -LO https://github.com/neovim/neovim-releases/releases/download/v0.10.4/nvim-linux-x86_64.tar.gz; \
    tar xzf nvim-linux-x86_64.tar.gz; \
    cp -r nvim-linux-x86_64/* /usr; \
    rm -rf nvim-linux-x86_64 nvim-linux-x86_64.tar.gz; \
  fi && \
  git clone https://github.com/nvim-lua/kickstart.nvim.git "${XDG_CONFIG_HOME:-$HOME/.config}"/nvim && \
  nvim --headless "+Lazy! sync" +qa && \
  # fish and bob
  curl -L https://get.oh-my.fish > install && \
  fish install --noninteractive && \
  fish -c "omf install bobthefish" && \
  mkdir -p ~/.config/fish/functions && \
  echo "function l\n  ls -Alh \$argv\nend" > ~/.config/fish/functions/l.fish && \
  rm install && \
  nala install ant fontconfig -y && \
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
COPY ./react-live-game-room/build /usr/local/tomcat/webapps/ROOT/gameServer/live
COPY ./react-mmai/build /usr/local/tomcat/webapps/ROOT/gameServer/mmai

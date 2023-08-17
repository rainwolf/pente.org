FROM tomcat:9-jdk21-openjdk-slim

# - mount /etc/dsg/, /var/log/tomcat, /var/log/dsg outside of container, maybe /var/lib/dsg

# fish and bob
RUN apt update
RUN apt install -y curl fish git
RUN curl -L https://get.oh-my.fish > install
RUN fish install --noninteractive
RUN fish -c "omf install bobthefish"
RUN mkdir -p ~/.config/fish/functions
RUN echo "function l\n  ls -Alh \$argv\nend" > ~/.config/fish/functions/l.fish

# AstroNvim
RUN curl -LO https://github.com/matsuu/neovim-aarch64-appimage/releases/download/v0.9.0/nvim-v0.9.0.appimage
RUN chmod u+x nvim-v0.9.0.appimage
RUN ./nvim-v0.9.0.appimage --appimage-extract
RUN cp -r squashfs-root/* /
RUN rm -rf squashfs-root nvim-v0.9.0.appimage
RUN git clone --depth 1 https://github.com/AstroNvim/AstroNvim ~/.config/nvim
RUN nvim --headless +PlugInstall +qall

RUN apt install -y ant
RUN mkdir -p /usr/local/tomcat/webapps/ROOT
COPY dsg_src/httpdocs/ /usr/local/tomcat/webapps/ROOT/
COPY dsg_src/lib/* /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/
RUN mkdir -p /usr/local/tomcat/webapps/tmp_src
COPY dsg_src/java /usr/local/tomcat/webapps/tmp_src
COPY build-docker.xml /usr/local/tomcat/webapps/
RUN rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/opengl/
RUN rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/gameServer/tourney/test/
RUN rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/message/test/
RUN rm -rf /usr/local/tomcat/webapps/tmp_src/org/pente/turnBased/test/
RUN ant -f /usr/local/tomcat/webapps/build-docker.xml
RUN rm -rf /usr/local/tomcat/webapps/tmp_src

RUN mkdir -p /var/lib/dsg/gameServer/game
RUN mkdir -p /var/lib/dsg/gameServer/player

ARG ENV=""
RUN mv /usr/local/tomcat/webapps/ROOT/META-INF/${ENV}context.xml /usr/local/tomcat/webapps/ROOT/META-INF/context.xml

# make sure they're built
COPY ./react-live-game-room/build /usr/local/tomcat/webapps/ROOT/gameServer/live
COPY ./react-mmai/build /usr/local/tomcat/webapps/ROOT/gameServer/mmai

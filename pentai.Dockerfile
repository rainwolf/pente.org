FROM alpine:3.10

COPY pentai.tar.gz /

#RUN apk add python2 gcc musl-dev python2-dev libffi-dev git \
#    py2-gst gst-plugins-good sdl2_image-dev sdl2_mixer-dev \
#    sdl2_ttf-dev py2-opengl mesa-dev && \
#    mkdir -p ~/.local/lib && \
#    wget -P ~/.local/lib https://bootstrap.pypa.io/pip/2.7/get-pip.py && \
#    python2.7 ~/.local/lib/get-pip.py --user && \
#    ~/.local/bin/pip2.7 install Cython==0.25.2 zc.zlibstorage ZODB && \
#    ~/.local/bin/pip2.7 install kivy==1.10.1 && \
#    tar xzf pentai.tar.gz && rm pentai.tar.gz && \
#    find /pentai/ -name "*.so" -exec rm {} \; && \
#    cd /pentai && python ./setup.py build_ext --inplace

RUN apk add python2 gcc python2-dev musl-dev libffi-dev && \
    mkdir -p ~/.local/lib && \
    wget -P ~/.local/lib https://bootstrap.pypa.io/pip/2.7/get-pip.py && \
    python2.7 ~/.local/lib/get-pip.py --user && \
    ~/.local/bin/pip2.7 install Cython==0.25.2 zc.zlibstorage ZODB && \
    tar xzf pentai.tar.gz && rm pentai.tar.gz && \
    find /pentai/ -name "*.so" -exec rm {} \; && \
    cd /pentai && python ./setup.py build_ext --inplace && \
    apk del gcc python2-dev musl-dev libffi-dev

CMD ["python", "/pentai/pentaiplayer.py"]

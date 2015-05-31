function openwin(plugin, port, width, height, guest) {
    url = '/gameServer/ad.jsp?plugin=' + plugin + '&port=' + port +
        '&size=' + width;
    if (guest) {
        url = url + '&guest=' + guest;
    }
    screenX = 0;
    screenY = 0;
    if (screen.width) {
        screenX = (screen.width - width) / 2;
        screenY = (screen.height - height) / 2;
    }
    else {
        screenX = 100;
        screenY = 100;
    }
    window.open(url, '_blank',
        'toolbar=no,status=yes,resizable=yes,screenX=' + screenX + ',left=' + screenX + ',screenY=' +
        screenY + 'top=' + screenY + ',width=' + width + ',height=' + height); 
}  

function handlePlay(plugin, size, guest) {

    var port = 16000;

    // if clicking a link that contains a list of lobbies
    if (document.mainPlayForm) {

        // if only one port
        if (document.mainPlayForm.port) {
            port = document.mainPlayForm.port.value;
        }
        // else use whatever lobby user selected
        else {
            port = document.mainPlayForm.lobbies.options[document.mainPlayForm.lobbies.selectedIndex].value;
        }
    }

    var width = 800;
    var height = 590;

    if (size == '640') {
        width = 650;
        height = 485;
    }
    openwin(plugin, port, width, height, guest);
}

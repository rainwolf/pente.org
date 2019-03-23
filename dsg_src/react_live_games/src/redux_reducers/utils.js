import React from 'react';

const server = "https://www.pente.org";

function crownimage(c) {
    if (c === 0) { return ""; }
    switch (c) {
        case 1: return <img alt="crown" src={server + '/gameServer/images/crown.gif'}/>;
        case 2: return <img alt="crown" src={server + '/gameServer/images/scrown.gif'}/>;
        case 3: return <img alt="crown" src={server + '/gameServer/images/bcrown.gif'}/>;
        default: return <img alt="crown" src={server + '/gameServer/images/kothcrown' + (c-3) + '.png'}/>;
    }
}
function userhtml(user) {
    return (
        <span className="name">
            <a href={server + "/gameServer/profile?viewName=" + user.name} target="_blank" rel="noopener noreferrer">
                { user.subscriber?(
                    <span style={{fontWeight:'bold', color:'#'+rgb2hex(user.name_color)}}>
                        {user.name}
                    </span> ):user.name
                }
            </a> &nbsp;
            {crownimage(user.crown)}
        </span>
    );
}
function rating(game) {
    if (game === undefined) return ""; 
    const r = this.game_ratings[game];
    let gif = "/gameServer/images/ratings_";
    if (r >= 1900) {
        gif += "red.gif";
    }
    else if (r >= 1700) {
        gif += "yellow.gif";
    }
    else if (r >= 1400) {
        gif += "blue.gif";
    }
    else if (r >= 1000) {
        gif += "green.gif";
    }
    else {
        gif += "gray.gif";
    }
    return (
        <div>
            <img alt="rating" src={server + gif}/>
            &nbsp; {r}
        </div>
    );
}
function avatar(subscriber, name) {
    if (subscriber) {
        return server+"/gameServer/avatar?name="+name;
    }
    return "";
}
export function processUser(userdata, state) {
    let user = { 
        name: userdata.player,
        game_ratings: {},
        subscriber: userdata.dsgPlayerData.subscriberLevel > 0,
        name_color: 0,
        crown: 0
    };
    if (user.subscriber) {
        user.name_color = userdata.dsgPlayerData.nameColor.value;
        // console.log('color: ' + user.name_color.toString(16))
    }
    let crown = 0;
    for(let i = 0; i<userdata.dsgPlayerData.gameData.length; i++) {
        let gameData = userdata.dsgPlayerData.gameData[i];
        if (gameData.computer === 'Y') { continue; }
        user.game_ratings[gameData.game] = Math.round(gameData.rating);
        if (crown === 0) {
            crown = gameData.tourneyWinner;
        } else if (gameData.tourneyWinner > 0) {
            if (gameData.tourneyWinner < 4) {
                crown = Math.min(crown, gameData.tourneyWinner);
            } else if (crown > 3) {
                crown = Math.max(crown, gameData.tourneyWinner); 
            }
        }
    }
    user.crown = crown;
    user.userhtml = userhtml(user);
    user.avatar = avatar(user.subscriber, user.name);
    user.rating = rating;
    state.users = { ...state.users, [user.name]: user };
}
function rgb2hex(n) {
    const b = n & 255, g = (n >> 8) & 255, r = (n >> 16) & 255;
    const f = s => { const h = s.toString(16); if (h.length < 2) { return '0'+h; } else { return h; }};
    // console.log(f(r)+f(g)+f(b))
    return f(r)+f(g)+f(b);
}
        
export function addRoomMessage(data, state) {
    const messages = state.room_messages.slice();
    const user = state.users[data.player];
    messages.push( {
        message: data.text,
        player: user
    });
    state.room_messages = messages;
}

export function exitUser(name, state) {
    let {[name]: value, ...rest} = state.users;
    state.users = rest;
}

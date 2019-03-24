import React from 'react';

const server = "https://www.pente.org";

class User {
    constructor(userdata) {
        this.name = userdata.name;
        this.game_ratings = {};
        this.subscriber = userdata.subscriberLevel > 0;
        this.name_color= 0;
        this.crown = 0;
        this.updateUser(userdata);
    }
    
    updateUser = (userdata) => {
        if (this.subscriber) {
            this.name_color = userdata.nameColor.value;
        }
        let crown = 0;
        for(let i = 0; i<userdata.gameData.length; i++) {
            let gameData = userdata.gameData[i];
            if (gameData.computer === 'Y') { continue; }
            this.game_ratings[gameData.game] = Math.round(gameData.rating);
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
        this.crown = crown;
        this.userhtml = this.#userhtml();
        this.avatar = this.#avatar();
    };
    #rgb2hex = (n) => {
        const b = n & 255, g = (n >> 8) & 255, r = (n >> 16) & 255;
        const f = s => { const h = s.toString(16); if (h.length < 2) { return '0'+h; } else { return h; }};
        // console.log(f(r)+f(g)+f(b))
        return f(r)+f(g)+f(b);
    };

    #crownimage = () => {
        const c = this.crown;
        if (c === 0) { return ""; }
        switch (c) {
            case 1: return <img alt="crown" src={server + '/gameServer/images/crown.gif'}/>;
            case 2: return <img alt="crown" src={server + '/gameServer/images/scrown.gif'}/>;
            case 3: return <img alt="crown" src={server + '/gameServer/images/bcrown.gif'}/>;
            default: return <img alt="crown" src={server + '/gameServer/images/kothcrown' + (c-3) + '.png'}/>;
        }
    };
    #userhtml = () => {
        return (
            <span className="name">
            <a href={server + "/gameServer/profile?viewName=" + this.name} target="_blank" rel="noopener noreferrer">
                { this.subscriber?(
                    <span style={{fontWeight:'bold', color:'#'+this.#rgb2hex(this.name_color)}}>
                        {this.name}
                    </span> ):this.name
                }
            </a> &nbsp;
                {this.#crownimage()}
        </span>
        );
    };
    rating = (game) => {
        if (game === undefined) return "";
        const r = this.game_ratings[game];
        if (r === undefined) return "";
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
            <span><img alt="rating" src={server + gif}/> &nbsp; {r}</span>
        );
    };
    #avatar = () => {
        if (this.subscriber) {
            return server+"/gameServer/avatar?name="+this.name;
        }
        return "";
    }
}

export default User;
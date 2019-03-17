

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
    state.users = { ...state.users, [user.name]: user };
}
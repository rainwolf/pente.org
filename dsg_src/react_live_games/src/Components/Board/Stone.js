import React from 'react';

function stone(props) {
    return (
        <svg key={1} height={props.size*1.5} width={props.size*1.5}>
            <radialGradient id={props.id} cx="40%" cy="40%" r="50%">
                <stop offset="0%" stopColor='var(--color1)' />
                <stop offset="100%" stopColor='var(--color2)' />
            </radialGradient>
            <filter id="f2" x="0" y="0" width="150%" height="150%">
                <feOffset result="offOut" in="SourceAlpha" dx={4} dy={4} />
                <feGaussianBlur result="blurOut" in="offOut" stdDeviation="3" />
                <feBlend in="SourceGraphic" in2="blurOut" mode="normal" />
            </filter>
            <circle cx={props.size/2} cy={props.size/2} r={props.size/2}
                    fill={"url(#"+props.id+")"} fillOpacity={props.opacity}
                    pointerEvents={'none'} filter={"url(#f2)"}/>
        </svg>
    );
}

export default stone;
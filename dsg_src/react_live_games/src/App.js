import React, { Component } from 'react';
// import logo from './logo.svg';
import './App.css';
import Test from './test/Test';

class App extends Component {
  render() {
    return (
        <Test 
            size={500} 
            gridColor="#0000f0"
        />
    );
  }
}

export default App;

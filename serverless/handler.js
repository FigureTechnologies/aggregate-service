'use strict'
module.exports.hello = (event, context, callback) => {
    const response = {
        body: JSON.stringify({
            message: 'Hello World',
            input: event,
        }),
    };
    callback(null, response);
};

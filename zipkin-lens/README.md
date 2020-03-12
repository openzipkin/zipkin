# Zipkin Lens

Zipkin-lens is an alternative UI for [Zipkin](https://github.com/openzipkin/zipkin), which based on React, Netflix/vizceral and chartjs.

Here are a couple example screen shots:

<img width="1920" alt="Search Screen" src="https://user-images.githubusercontent.com/64215/49579677-4602de00-f990-11e8-81b7-dd782ce91227.png">
<img width="1920" alt="Trace Detail Screen" src="https://user-images.githubusercontent.com/64215/49579684-4d29ec00-f990-11e8-8799-5c53a503413e.png">

## Quick start

This project was bootstrapped with [Create React App](https://github.com/facebook/create-react-app).

In the project directory, you can run:

### `npm start`

Runs the app in the development mode.<br />
Open [http://localhost:3000](http://localhost:3000) to view it in the browser.

The page will reload if you make edits.<br />
You will also see any lint errors in the console.

### `npm test`

Launches the test runner in the interactive watch mode.<br />
See the section about [running tests](https://facebook.github.io/create-react-app/docs/running-tests) for more information.

### `npm run build`

Builds the app for production to the `build` folder.<br />
It correctly bundles React in production mode and optimizes the build for the best performance.

## Localization

We use [LinguiJS](https://lingui.js.org/) for localization of the UI. Translations for strings are
found in the JSON files under [here](./src/translations). The Javascript files in the directory are
compiled from the JSON files. We're always excited to have help maintaining these translations - if
you see a string in the UI that is not translated or mistranslated, please feel free to send a PR to
the JSON file to fix it. If you can, please run `yarn run compile` to also compile the translation
into the output. If it's tedious to set up an environment for it, though, don't worry we'll take care
of it.

### Adding a new locale

To add a new translated locale, first edit [.linguirc](./.linguirc) and add the locale to the
`locales` section. Next, run `yarn run extract` to extract a new file under `src/translations` for
the locale. Translate as many strings in the JSON file as you can. Then run `yarn run compile` to
compile the strings.

Finally, edit [App.jsx](./src/components/App/App.jsx) and
[LanguageSelector.jsx](./src/components/App/LanguageSelector.jsx) to import the new translation and
add an entry to the language selector respectively.

import i18n from 'i18next'
import {initReactI18next} from "react-i18next";
import LanguageDetector from 'i18next-browser-languagedetector'
import fr_translation from './fr/messages.json'
import es_translation from './es/messages.json'

import cn from './zh-cn/messages.json'


const resources = {
  es: {
    translation: es_translation
  },
  fr:{
    translation: fr_translation
  },
  zh_cn: {
    translation:cn
  }
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init(
    {
      resources
    }
  )

export default i18n

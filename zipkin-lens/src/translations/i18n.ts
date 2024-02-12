import i18n from 'i18next'
import {initReactI18next} from "react-i18next";
import LanguageDetector from 'i18next-browser-languagedetector'
import messages_fr from './fr/translations.json'
import messages_es from './es/translations.json'
import messages_zh from './zh-cn/translations.json'
import messages_en from './en/translations.json'


const resources = {
  es: {
    translation: messages_es
  },
  fr:{
    translation: messages_fr
  },
  zh_cn: {
    translation: messages_zh
  },
  en:{
    translation: messages_en
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

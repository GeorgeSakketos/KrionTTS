package com.krion.tts.domain

object ModelCatalog {
    val models: List<LanguageModel> = listOf(
        LanguageModel(
            id = "vits_piper_en_us_libritts_r_medium",
            languageCode = "en-US",
            displayName = "English (US)",
            modelName = "Piper LibriTTS-R Medium (VITS)",
            description = "Piper English multi-speaker model (904 speakers).",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-libritts_r-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_coqui_en_vctk",
            languageCode = "en-US",
            displayName = "English (US)",
            modelName = "Coqui VCTK (VITS)",
            description = "Coqui English multi-speaker model (109 speakers).",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-coqui-en-vctk.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_es_es_glados_medium",
            languageCode = "es-ES",
            displayName = "Spanish (Spain)",
            modelName = "Piper Glados Medium (VITS)",
            description = "Piper Spanish (Spain) medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-glados-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_de_de_thorsten_medium",
            languageCode = "de-DE",
            displayName = "German (Germany)",
            modelName = "Piper Thorsten Medium (VITS)",
            description = "Piper German medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-de_DE-thorsten-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_fr_fr_tom_medium",
            languageCode = "fr-FR",
            displayName = "French (France)",
            modelName = "Piper Tom Medium (VITS)",
            description = "Piper French medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fr_FR-tom-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_ru_ru_ruslan_medium",
            languageCode = "ru-RU",
            displayName = "Russian",
            modelName = "Piper Ruslan Medium (VITS)",
            description = "Piper Russian medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-ruslan-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_uk_ua_ukrainian_tts_medium",
            languageCode = "uk-UA",
            displayName = "Ukrainian",
            modelName = "Piper Ukrainian TTS Medium (VITS)",
            description = "Piper Ukrainian medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-uk_UA-ukrainian_tts-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_mms_tha",
            languageCode = "th-TH",
            displayName = "Thai",
            modelName = "MMS Thai (VITS)",
            description = "Open-source MMS Thai model for offline generation.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mms-tha.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_it_it_paola_medium",
            languageCode = "it-IT",
            displayName = "Italian (Italy)",
            modelName = "Piper Paola Medium (VITS)",
            description = "Piper Italian medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-it_IT-paola-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_pt_br_jeff_medium",
            languageCode = "pt-BR",
            displayName = "Portuguese (Brazil)",
            modelName = "Piper Jeff Medium (VITS)",
            description = "Piper Portuguese (Brazil) medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pt_BR-jeff-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_nl_nl_pim_medium",
            languageCode = "nl-NL",
            displayName = "Dutch (Netherlands)",
            modelName = "Piper Pim Medium (VITS)",
            description = "Piper Dutch medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-nl_NL-pim-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_tr_tr_fahrettin_medium",
            languageCode = "tr-TR",
            displayName = "Turkish (Türkiye)",
            modelName = "Piper Fahrettin Medium (VITS)",
            description = "Piper Turkish medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-tr_TR-fahrettin-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_sv_se_lisa_medium",
            languageCode = "sv-SE",
            displayName = "Swedish (Sweden)",
            modelName = "Piper Lisa Medium (VITS)",
            description = "Piper Swedish medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-sv_SE-lisa-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_id_id_news_tts_medium",
            languageCode = "id-ID",
            displayName = "Indonesian",
            modelName = "Piper News TTS Medium (VITS)",
            description = "Piper Indonesian medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-id_ID-news_tts-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        ),
        LanguageModel(
            id = "vits_piper_ar_jo_kareem_medium",
            languageCode = "ar-JO",
            displayName = "Arabic (Jordan)",
            modelName = "Piper Kareem Medium (VITS)",
            description = "Piper Arabic medium-quality offline voice model.",
            archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ar_JO-kareem-medium.tar.bz2",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
        )
    )
}

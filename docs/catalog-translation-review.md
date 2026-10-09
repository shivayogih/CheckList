# Catalogue translation review

Item and category names from the India master catalogue that a native speaker should check
before release (CL-107). **Generated** by `python3 tools/seed/build_india_catalog.py`; do not
edit by hand. Fixes go into `tools/seed/src_data/translations/<tag>.json` (then regenerate)
or, for an urgent fix, straight into `data/src/main/assets/seed/i18n/<tag>.json` plus the same
edit in the source file so the next regeneration keeps it.

Every name is a machine-assisted draft. The lists below are the ones the translator was
least sure of, plus any `fallback` rows where English is shown for lack of a translation.

Catalogue: 31 categories, 598 seed items, 7 languages.

## How to review

1. Is it the word people actually say when writing a shopping or packing list?
2. Brand-like or loan words (ORS, LED, Wi-Fi) may stay in Latin script when that is normal.
3. Do not translate Latin-script aliases; they exist so typed English still finds the item.

## Kannada (`kn`): 27 to review, translations present

| Id | English | Draft | Why |
|---|---|---|---|
| `ITM0136` | Dill leaves / sabbasige soppu | ಸಬ್ಬಸಿಗೆ ಸೊಪ್ಪು | dill leaf local names vary |
| `ITM0158` | Quail eggs | ಗೌಜಲ ಮೊಟ್ಟೆ | quail egg Kannada name uncertain |
| `ITM0163` | Lamb | ಲ್ಯಾಂಬ್ | lamb transliterated, locally often just mutton |
| `ITM0169` | Squid | ಸ್ಕ್ವಿಡ್ | squid Kannada transliterated |
| `ITM0170` | Clams | ಕಪ್ಪೆಚಿಪ್ಪು | clams local name varies by coast |
| `ITM0171` | Mussels | ಮಸ್ಸೆಲ್ಸ್ | mussels Kannada transliterated |
| `ITM0196` | Chakli | ಚಕ್ಕುಲಿ | chakli/chakralu Telugu name varies |
| `ITM0197` | Murukku | ಮುರುಕ್ಕು | murukku vs chakli naming differs by region |
| `ITM0216` | Star anise | ಚಕ್ರಮೊಗ್ಗು | star anise local names vary |
| `ITM0074` | Knol-khol | ನವಿಲುಕೋಸು | knol-khol Telugu name uncertain |
| `ITM0091` | Corn on the cob | ಮೆಕ್ಕೆಜೋಳ | corn on cob wording |
| `ITM0100` | Muskmelon | ಖರ್ಬೂಜ | muskmelon local name varies |
| `ITM0079` | Broad beans / avarekalu | ಅವರೆಕಾಯಿ | broad beans local name varies |
| `ITM0140` | Basale soppu / Malabar spinach | ಬಸಳೆ ಸೊಪ್ಪು | Malabar spinach name varies |
| `ITM0001` | Raw rice | ಅಕ್ಕಿ | raw rice: Kannada/Telugu use generic rice word |
| `ITM0064` | Raw banana | ಬಾಳೆಕಾಯಿ | raw banana Malayalam name varies |
| `ITM0253` | Packaged drinking water | ಬಾಟಲ್ ನೀರು | packaged water wording |
| `ITM0365` | Mosquito repellent refill | ಸೊಳ್ಳೆ ರೀಫಿಲ್ | shortened mosquito refill |
| `ITM0368` | Sterile gauze | ಗಾಜ್ ಬಟ್ಟೆ | gauze Malayalam transliterated |
| `ITM0386` | Feeding bottle | ಹಾಲಿನ ಬಾಟಲ್ | feeding bottle Telugu native word used |
| `ITM0545` | String lights | ಸೀರಿಯಲ್ ಲೈಟ್ | string lights colloquial serial light |
| `ITM0548` | Pay electricity bill | ಕರೆಂಟ್ ಬಿಲ್ ಕಟ್ಟುವುದು | task phrasing, verb form |
| `ITM0559` | Check insurance renewal | ವಿಮೆ ನವೀಕರಣ ಪರಿಶೀಲನೆ | task phrasing |
| `ITM0560` | Clean email inbox | ಇಮೇಲ್ ಇನ್ಬಾಕ್ಸ್ ಸ್ವಚ್ಛತೆ | task phrasing |
| `ITM0020` | Puffed rice / mandakki | ಮಂಡಕ್ಕಿ | puffed rice names vary |
| `ITM0132` | Amaranth leaves / dantina soppu | ದಂಟಿನ ಸೊಪ್ಪು | amaranth names vary |
| `ITM0130` | Spinach / palak | ಪಾಲಕ್ ಸೊಪ್ಪು | palak Malayalam name |

## Hindi (`hi`): 10 to review, translations present

| Id | English | Draft | Why |
|---|---|---|---|
| `ITM0062` | Ash gourd | पेठा | पेठा vs कुम्हड़ा: region-dependent word for ash gourd |
| `ITM0075` | Chayote / chow chow | चौ चौ | चौ चौ is the commonly used name; confirm for north India |
| `ITM0088` | Tender coconut | हरा नारियल | हरा नारियल used for tender coconut; check |
| `ITM0136` | Dill leaves / sabbasige soppu | सोया साग | dill: सोया साग vs सुवा |
| `ITM0140` | Basale soppu / Malabar spinach | पोई साग | Malabar spinach: पोई साग |
| `ITM0163` | Lamb | लैम्ब | Lamb transliterated as लैम्ब |
| `ITM0170` | Clams | क्लैम | clams transliterated |
| `ITM0305` | Disinfectant | फिनायल | Disinfectant rendered as फिनायल (common brand-like term) |
| `ITM0403` | Pet medication prescribed by vet | पशु डॉक्टर की दवा | paraphrased to fit row |
| `ITM0491` | Utility bill | घर के बिल | Utility bill rendered as घर के बिल |

## Tamil (`ta`): 17 to review, translations present

| Id | English | Draft | Why |
|---|---|---|---|
| `ITM0012` | Jowar flour | சோள மாவு | Jowar flour: சோள மாவு (சோளம் = jowar in TN); confirm vs corn |
| `ITM0028` | Kabuli chana | வெள்ளை கொண்டைக்கடலை | Kabuli chana: வெள்ளை கொண்டைக்கடலை |
| `ITM0033` | Cowpeas / alasande | காராமணி | Cowpeas: காராமணி / தட்டைப்பயறு |
| `ITM0062` | Ash gourd | வெள்ளைப் பூசணிக்காய் | Ash gourd: வெள்ளைப் பூசணிக்காய் |
| `ITM0132` | Amaranth leaves / dantina soppu | தண்டுக்கீரை | Amaranth: தண்டுக்கீரை vs அரைக்கீரை |
| `ITM0136` | Dill leaves / sabbasige soppu | சதகுப்பைக் கீரை | Dill: சதகுப்பைக் கீரை (rare word; many say சோயா கீரை) |
| `ITM0140` | Basale soppu / Malabar spinach | பசலைக் கீரை | Basale/Malabar spinach: பசலைக் கீரை |
| `ITM0141` | Basil | பேசில் | Basil: பேசில் vs துளசி |
| `ITM0163` | Lamb | ஆட்டுக்குட்டி இறைச்சி | Lamb wording |
| `ITM0171` | Mussels | கல்லுமக்காய் | Mussels: கல்லுமக்காய் (Malabar/coastal term, check) |
| `ITM0196` | Chakli | சக்கலி | Chakli: சக்கலி transliteration |
| `ITM0210` | Coriander seeds | மல்லி | Coriander seeds: மல்லி (also தனியா) |
| `ITM0403` | Pet medication prescribed by vet | கால்நடை மருத்துவர் மருந்து | paraphrased to fit row |
| `ITM0454` | Rain poncho | ரெயின் பொன்சோ | Rain poncho transliterated |
| `ITM0488` | Relieving letter | பணிவிடுவிப்புக் கடிதம் | Relieving letter: formal rendering |
| `ITM0525` | Mulch | மூடாக்கு | Mulch: மூடாக்கு (agri term) |
| `ITM0548` | Pay electricity bill | மின்கட்டணம் செலுத்து | Action items use imperative short form |

## Telugu (`te`): 27 to review, translations present

| Id | English | Draft | Why |
|---|---|---|---|
| `ITM0136` | Dill leaves / sabbasige soppu | సోయకూర | dill leaf local names vary |
| `ITM0158` | Quail eggs | పిట్ట గుడ్లు | quail egg Kannada name uncertain |
| `ITM0163` | Lamb | లాంబ్ | lamb transliterated, locally often just mutton |
| `ITM0169` | Squid | స్క్విడ్ | squid Kannada transliterated |
| `ITM0170` | Clams | ఆల్చిప్పలు | clams local name varies by coast |
| `ITM0171` | Mussels | మస్సెల్స్ | mussels Kannada transliterated |
| `ITM0196` | Chakli | చక్రాలు | chakli/chakralu Telugu name varies |
| `ITM0197` | Murukku | మురుకులు | murukku vs chakli naming differs by region |
| `ITM0216` | Star anise | అనాసపువ్వు | star anise local names vary |
| `ITM0074` | Knol-khol | నూకోల్ | knol-khol Telugu name uncertain |
| `ITM0091` | Corn on the cob | మొక్కజొన్న కంకి | corn on cob wording |
| `ITM0100` | Muskmelon | ఖర్బూజ | muskmelon local name varies |
| `ITM0079` | Broad beans / avarekalu | చిక్కుడు కాయలు | broad beans local name varies |
| `ITM0140` | Basale soppu / Malabar spinach | బచ్చలికూర | Malabar spinach name varies |
| `ITM0001` | Raw rice | బియ్యం | raw rice: Kannada/Telugu use generic rice word |
| `ITM0064` | Raw banana | అరటికాయ | raw banana Malayalam name varies |
| `ITM0253` | Packaged drinking water | మినరల్ వాటర్ | packaged water wording |
| `ITM0365` | Mosquito repellent refill | దోమల రీఫిల్ | shortened mosquito refill |
| `ITM0368` | Sterile gauze | గాజ్ బట్ట | gauze Malayalam transliterated |
| `ITM0386` | Feeding bottle | పాల సీసా | feeding bottle Telugu native word used |
| `ITM0545` | String lights | సీరియల్ లైట్లు | string lights colloquial serial light |
| `ITM0548` | Pay electricity bill | కరెంట్ బిల్లు కట్టడం | task phrasing, verb form |
| `ITM0559` | Check insurance renewal | బీమా రెన్యూవల్ చెక్ | task phrasing |
| `ITM0560` | Clean email inbox | ఇమెయిల్ ఇన్బాక్స్ క్లీన్ | task phrasing |
| `ITM0020` | Puffed rice / mandakki | మరమరాలు | puffed rice names vary |
| `ITM0132` | Amaranth leaves / dantina soppu | తోటకూర | amaranth names vary |
| `ITM0130` | Spinach / palak | పాలకూర | palak Malayalam name |

## Marathi (`mr`): 12 to review, translations present

| Id | English | Draft | Why |
|---|---|---|---|
| `ITM0038` | Rock salt | शेंदेलोण | Rock salt as शेंदेलोण; some say सैंधव |
| `ITM0075` | Chayote / chow chow | चौ चौ | Chayote: चौ चौ vs घेवडा/श्रावणघेवडा |
| `ITM0079` | Broad beans / avarekalu | वालाच्या शेंगा | Broad beans: वालाच्या शेंगा (avarekalu is Kannada) |
| `ITM0132` | Amaranth leaves / dantina soppu | माठ | Amaranth: माठ vs राजगिरा |
| `ITM0140` | Basale soppu / Malabar spinach | मायाळू | Malabar spinach: मायाळू |
| `ITM0158` | Quail eggs | लावा पक्ष्याची अंडी | Quail eggs: लावा पक्ष्याची अंडी |
| `ITM0169` | Squid | माकूळ | Squid: माकूळ |
| `ITM0170` | Clams | कालवे | Clams: कालवे |
| `ITM0171` | Mussels | शिंपले | Mussels: शिंपले (regional, also शिणाणे) |
| `ITM0195` | Namkeen / mixture | फरसाण | Namkeen/mixture: फरसाण (chivda also common) |
| `ITM0403` | Pet medication prescribed by vet | पशुवैद्याने दिलेले औषध | paraphrased to fit row |
| `ITM0525` | Mulch | मल्चिंग | Mulch: मल्चिंग transliterated |

## Malayalam (`ml`): 27 to review, translations present

| Id | English | Draft | Why |
|---|---|---|---|
| `ITM0136` | Dill leaves / sabbasige soppu | ചതകുപ്പ | dill leaf local names vary |
| `ITM0158` | Quail eggs | കാടമുട്ട | quail egg Kannada name uncertain |
| `ITM0163` | Lamb | ലാംബ് | lamb transliterated, locally often just mutton |
| `ITM0169` | Squid | കൂന്തൽ | squid Kannada transliterated |
| `ITM0170` | Clams | കക്ക | clams local name varies by coast |
| `ITM0171` | Mussels | കല്ലുമ്മക്കായ | mussels Kannada transliterated |
| `ITM0196` | Chakli | ചക്ലി | chakli/chakralu Telugu name varies |
| `ITM0197` | Murukku | മുറുക്ക് | murukku vs chakli naming differs by region |
| `ITM0216` | Star anise | തക്കോലം | star anise local names vary |
| `ITM0074` | Knol-khol | കോൾറാബി | knol-khol Telugu name uncertain |
| `ITM0091` | Corn on the cob | ചോളം | corn on cob wording |
| `ITM0100` | Muskmelon | മസ്ക് മെലൺ | muskmelon local name varies |
| `ITM0079` | Broad beans / avarekalu | അമരയ്ക്ക | broad beans local name varies |
| `ITM0140` | Basale soppu / Malabar spinach | വള്ളിച്ചീര | Malabar spinach name varies |
| `ITM0001` | Raw rice | പച്ചരി | raw rice: Kannada/Telugu use generic rice word |
| `ITM0064` | Raw banana | ഏത്തക്കായ | raw banana Malayalam name varies |
| `ITM0253` | Packaged drinking water | കുപ്പിവെള്ളം | packaged water wording |
| `ITM0365` | Mosquito repellent refill | കൊതുക് റീഫിൽ | shortened mosquito refill |
| `ITM0368` | Sterile gauze | ഗോസ് | gauze Malayalam transliterated |
| `ITM0386` | Feeding bottle | ഫീഡിംഗ് ബോട്ടിൽ | feeding bottle Telugu native word used |
| `ITM0545` | String lights | സീരിയൽ ലൈറ്റ് | string lights colloquial serial light |
| `ITM0548` | Pay electricity bill | കറന്റ് ബിൽ അടയ്ക്കുക | task phrasing, verb form |
| `ITM0559` | Check insurance renewal | ഇൻഷുറൻസ് പുതുക്കൽ നോക്കുക | task phrasing |
| `ITM0560` | Clean email inbox | ഇമെയിൽ ഇൻബോക്സ് വൃത്തിയാക്കുക | task phrasing |
| `ITM0020` | Puffed rice / mandakki | അരിപ്പൊരി | puffed rice names vary |
| `ITM0132` | Amaranth leaves / dantina soppu | ചീര | amaranth names vary |
| `ITM0130` | Spinach / palak | പാലക് ചീര | palak Malayalam name |


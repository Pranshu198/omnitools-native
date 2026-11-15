package com.jackbhai.omnitools.nativ.games;

/** Clean word lists for Word Guess and Hangman (part 2). Common English
 *  words only, no proper nouns. FIVE_ANSWERS are the Word Guess targets;
 *  FIVE_GUESSES adds extra accepted guesses. HANGMAN words run 4-9 letters. */
public final class GameWords {
    private GameWords() {}

    public static final String[] FIVE_ANSWERS = {
        "about","above","actor","acute","admit","adopt","adult","after","again","agent",
        "agree","ahead","alarm","album","alert","alike","alive","allow","alone","along",
        "aloud","alpha","altar","alter","amber","ample","angel","angle","angry","ankle",
        "apart","apple","apply","apron","arena","argue","arise","armor","aroma","array",
        "arrow","aside","asset","atlas","audio","audit","avoid","awake","award","aware",
        "bacon","badge","badly","bagel","baker","banjo","barge","baron","basic","basil",
        "basin","basis","batch","bathe","baton","beach","beard","beast","beech","beefy",
        "begin","being","belly","below","bench","berry","bible","bicep","birth","bison",
        "black","blade","blame","bland","blank","blast","blaze","bleak","bleed","blend",
        "bless","blind","blink","bliss","block","blond","blood","bloom","blown","blues",
        "bluff","blunt","blush","board","boast","bonus","booth","booty","booze","borne",
        "bosom","bossy","botch","bough","bound","bowel","boxer","brace","braid","brain",
        "brake","brand","brash","brass","brave","bravo","brawl","brawn","bread","break",
        "breed","bribe","brick","bride","brief","bring","brink","brisk","broad","broil",
        "broke","brood","brook","broom","broth","brown","brush","brute","buddy","budge",
        "buggy","bugle","build","built","bulge","bulky","bully","bumps","bumpy","bunch",
        "bunny","burly","burnt","burst","busby","bushy","busty","butch","butte","buxom",
        "buyer","buzzy","bylaw","bytes","cabal","cabin","cable","cacao","cache","cadet",
        "cagey","cairn","caked","cakes","calif","calla","calls","calms","calve","camel",
        "cameo","canal","candy","caned","canes","canid","canna","canny","canoe","canon",
        "canso","caper","capes","caput","carat","carbs","cards","cared","carer","cares",
        "cargo","carol","carom","carps","carry","carve","cased","cases","casks","caste",
        "casts","catch","cater","cathy","caulk","cause","caved","caver","caves","cease",
        "cedar","celeb","cello","cells","celts","cents","chain","chair","chalk","champ",
        "chant","chaos","chaps","chard","charm","chart","chase","chasm","cheap","cheat",
        "check","cheek","cheep","cheer","chefs","chess","chest","chews","chewy","chick",
        "chide","chief","child","chili","chill","chime","chimp","china","chine","chink",
        "chins","chips","chirp","chits","chive","chock","choir","choke","chops","chord",
        "chore","chose","chunk","churn","chute","cider","cigar","cilia","cinch","cisco",
        "cited","cites","civic","civil","claim","clamp","clams","clang","clank","clans",
        "claps","clash","clasp","class","clave","claws","clays","clean","clear","cleat",
        "cleft","clerk","click","cliff","climb","clime","cling","clink","clips","cloak",
        "clock","clods","clogs","clone","close","cloth","cloud","clout","clove","clown",
        "clubs","cluck","clued","clues","clump","clung","clunk","coach","coals","coast",
        "coats","cobra","cocci","cocky","cocoa","cocos","codas","codec","coded","coder",
        "codes","codex","codon","coeds","coifs","coign","coils","coins","cokes","colds",
        "colic","colin","colon","color","colts","colza","comae","comal","comas","combe",
        "combi","combo","combs","comer","comes","comet","comfy","comic","comma","comte",
        "crane","grape","lemon","mango","melon","peach","plead","zebra"
    };

    public static final String[] FIVE_GUESSES = {
        "abide","abode","abort","abyss","acorn","acres","acrid","acted","adept","adieu",
        "adobe","adore","adorn","aegis","afire","afoot","afoul","agate","agave","agile",
        "aging","aglow","agony","ahead","ailed","aimed","aired","aisle","alarm","album",
        "alder","alert","algae","alien","align","alike","alive","allay","alley","allot",
        "alloy","aloud","alpha","altar","alter","altos","amass","amaze","amble","amend",
        "ament","amice","amici","amide","amido","amids","amies","amiga","amigo"
    };

    public static final String[] HANGMAN = {
        "apple","autumn","banana","bear","bicycle","bird","boat","book","bread","bridge",
        "brother","bus","butter","camera","car","castle","cat","chair","cheese","cherry",
        "child","city","clock","cloudy","comet","computer","copper","dance","deer","dog",
        "door","dream","drum","earth","elephant","engine","evening","family","father","field",
        "fire","fish","flower","flute","forest","friend","frog","galaxy","garden","glass",
        "golden","grapes","grass","guitar","happy","honey","horse","house","leaf","lemon",
        "light","lion","mango","market","melon","metal","milk","monkey","moon","morning",
        "mother","mountain","music","night","ocean","orange","orbit","paper","path","peach",
        "pencil","people","pepper","phone","piano","plane","planet","plum","rabbit","radio",
        "rainy","rice","river","road","rocket","salt","school","shadow","ship","silver",
        "sister","smile","snake","snow","song","space","spring","star","stone","storm",
        "street","student","sugar","summer","sun","sunny","table","teacher","television","tiger",
        "today","tower","train","tree","truck","trumpet","village","violin","watch","water",
        "wheat","wheel","wind","window","windy","winter","wolf","wood","world","zebra"
    };
}

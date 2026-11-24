// Typische Erkennungsergebnisse (deutsche/italienische Spracherkennung) → gemeinte Pflanze
export const PLANTS = [
  ['Gänseblümchen', 'Bellis perennis'], ['Löwenzahn', 'Taraxacum officinale'], ['Klatschmohn', 'Papaver rhoeas'],
  ['Kornblume', 'Centaurea cyanus'], ['Wiesen-Salbei', 'Salvia pratensis'], ['Echte Kamille', 'Matricaria chamomilla'],
  ['Margerite', 'Leucanthemum vulgare'], ['Roter Fingerhut', 'Digitalis purpurea'], ['Gewöhnliche Schafgarbe', 'Achillea millefolium'],
  ['Scharbockskraut', 'Ficaria verna'], ['Spitzwegerich', 'Plantago lanceolata'], ['Brennnessel', 'Urtica dioica'],
  ['Echter Lavendel', 'Lavandula angustifolia'], ['Rotbuche', 'Fagus sylvatica'], ['Stieleiche', 'Quercus robur'],
];
// [gehört, gemeinte botanische oder deutsche Bezeichnung]
export const HEARD = [
  // deutsche Erkennung, botanisch gesprochen
  ['Taraxakum Offizinale', 'Taraxacum officinale'], ['Tarak Sakum offizinale', 'Taraxacum officinale'], ['taraxacum offizielle', 'Taraxacum officinale'],
  ['Plantago Lanzeolata', 'Plantago lanceolata'], ['Plan Tago Lance olata', 'Plantago lanceolata'], ['Plantage lanceolata', 'Plantago lanceolata'],
  ['Achillea Millefolium', 'Achillea millefolium'], ['Akilea mille Folium', 'Achillea millefolium'], ['Achilles Millefolium', 'Achillea millefolium'],
  ['Matrikaria Kamomilla', 'Matricaria chamomilla'], ['Matricaria Camomilla', 'Matricaria chamomilla'], ['Matrix aria chamomilla', 'Matricaria chamomilla'],
  ['Leukantemum vulgare', 'Leucanthemum vulgare'], ['Löwe Kant hemum vulgare', 'Leucanthemum vulgare'], ['leucanthemum Wolga re', 'Leucanthemum vulgare'],
  ['Digitalis Purpur Rea', 'Digitalis purpurea'], ['Digitalis purpurea', 'Digitalis purpurea'], ['Digital ist Purpurea', 'Digitalis purpurea'],
  ['Fikaria Werner', 'Ficaria verna'], ['Ficaria Wärme', 'Ficaria verna'], ['vi Karya verna', 'Ficaria verna'],
  ['Papa wer Rosas', 'Papaver rhoeas'], ['Papaver Röas', 'Papaver rhoeas'], ['Papaver Reas', 'Papaver rhoeas'],
  ['Zentaurea Zyanus', 'Centaurea cyanus'], ['Centaurea Cyanos', 'Centaurea cyanus'], ['Zentaur Ära Ziana', 'Centaurea cyanus'],
  ['Salvia Pratensis', 'Salvia pratensis'], ['Salvia Prater ins', 'Salvia pratensis'], ['Salbei pratensis', 'Salvia pratensis'],
  ['Bellis Perennis', 'Bellis perennis'], ['Belis Peren is', 'Bellis perennis'], ['Bellis Paris', 'Bellis perennis'],
  ['Urtica dioika', 'Urtica dioica'], ['Urtika Dio ika', 'Urtica dioica'], ['Urlaub dioica', 'Urtica dioica'],
  ['Lavandula Angustifolia', 'Lavandula angustifolia'], ['Lavendula Angus die Folia', 'Lavandula angustifolia'],
  ['Fagus silvatica', 'Fagus sylvatica'], ['Fagus Sylvia Tika', 'Fagus sylvatica'], ['Quercus Robur', 'Quercus robur'], ['Kwerkus Robur', 'Quercus robur'], ['Quercus Robert', 'Quercus robur'],
  // italienische Erkennung, botanisch gesprochen
  ['taraxacum officinale', 'Taraxacum officinale'], ['achillea millefoglio', 'Achillea millefolium'], ['papaver reas', 'Papaver rhoeas'],
  ['centaurea ciano', 'Centaurea cyanus'], ['leucantemum volgare', 'Leucanthemum vulgare'], ['digitale purpurea', 'Digitalis purpurea'],
  ['matricaria camomilla', 'Matricaria chamomilla'], ['salvia pratense', 'Salvia pratensis'], ['plantago lanceolata', 'Plantago lanceolata'],
  ['bellis perenne', 'Bellis perennis'], ['ficaria verna', 'Ficaria verna'], ['urtica dioica', 'Urtica dioica'], ['lavanda angustifolia', 'Lavandula angustifolia'],
  // deutsche Namen
  ['Gänseblümchen', 'Gänseblümchen'], ['Löwenzahn', 'Löwenzahn'], ['Spitz Wegerich', 'Spitzwegerich'], ['Scharbocks Kraut', 'Scharbockskraut'],
  ['Schaf Garbe', 'Gewöhnliche Schafgarbe'], ['Schafgarbe', 'Gewöhnliche Schafgarbe'], ['Klatsch Mohn', 'Klatschmohn'], ['Kamille', 'Echte Kamille'],
  ['Wiesensalbei', 'Wiesen-Salbei'], ['roter Finger Hut', 'Roter Fingerhut'], ['Fingerhut', 'Roter Fingerhut'], ['Brennessel', 'Brennnessel'],
  ['Lavendel', 'Echter Lavendel'], ['Rot Buche', 'Rotbuche'], ['Stiel Eiche', 'Stieleiche'], ['Margarete', 'Margerite'], ['Korn Blume', 'Kornblume'],
  ['Scharbockskraut', 'Scharbockskraut'], ['Sharbox Kraut', 'Scharbockskraut'],
];
// falsche Antworten (dürfen NICHT als richtig zählen): [gehört, gefragte Pflanze]
export const WRONG = [
  ['Taraxacum officinale', 'Bellis perennis'], ['Löwenzahn', 'Gänseblümchen'], ['Kamille', 'Margerite'], ['Plantago lanceolata', 'Salvia pratensis'],
  ['Digitalis purpurea', 'Papaver rhoeas'], ['Quercus robur', 'Fagus sylvatica'], ['Rotbuche', 'Stieleiche'], ['Brennnessel', 'Spitzwegerich'],
  ['Achillea millefolium', 'Matricaria chamomilla'], ['Kornblume', 'Klatschmohn'], ['Urtica', 'Ficaria verna'], ['Salbei', 'Echter Lavendel'],
];
export const HARD = [
  ['Tara Xa kommt offizielle', 'Taraxacum officinale'], ['Taraxacum ofizi Nale', 'Taraxacum officinale'], ['Tarak sagt um offizinell', 'Taraxacum officinale'],
  ['Plantage Lanze o Lata', 'Plantago lanceolata'], ['Plan da go lanze ola da', 'Plantago lanceolata'],
  ['Achilles Mille Folio', 'Achillea millefolium'], ['Akkilea Millie Folium', 'Achillea millefolium'],
  ['Matt Rick Aria Kamel Milla', 'Matricaria chamomilla'], ['Matrikaria Kami Milla', 'Matricaria chamomilla'],
  ['Leuk Anton vulgär', 'Leucanthemum vulgare'], ['Leukanthemum Wulgare', 'Leucanthemum vulgare'],
  ['Digital es purpur Rhea', 'Digitalis purpurea'], ['Digi Talis Purpu rea', 'Digitalis purpurea'],
  ['Fick Aria Werner', 'Ficaria verna'], ['Fi Karia verna', 'Ficaria verna'],
  ['Papa war Rhea', 'Papaver rhoeas'], ['Papa wer Röas', 'Papaver rhoeas'],
  ['Zentaur Rhea Zion', 'Centaurea cyanus'], ['Zentaurea Zia Nus', 'Centaurea cyanus'],
  ['Salvia Prat Ensis', 'Salvia pratensis'], ['Salvia Pratt Enzis', 'Salvia pratensis'],
  ['Belis Perry Niss', 'Bellis perennis'], ['Bellis Pere Niss', 'Bellis perennis'],
  ['Urlaub die Oka', 'Urtica dioica'], ['Ur Tika Dioika', 'Urtica dioica'],
  ['Lavendel ab Gusti Folia', 'Lavandula angustifolia'], ['Lavandula Angusti Folia', 'Lavandula angustifolia'],
  ['Fakos Silva Tika', 'Fagus sylvatica'], ['Fagus Silva Tiger', 'Fagus sylvatica'],
  ['Quer kuss Robur', 'Quercus robur'], ['Quercus Robe', 'Quercus robur'],
  ['Schar Box Kraut', 'Scharbockskraut'], ['Spitz Wege Reich', 'Spitzwegerich'], ['Margarethe', 'Margerite'], ['Brenn Nessel', 'Brennnessel'],
  ['Schafgarben', 'Gewöhnliche Schafgarbe'], ['Klatsch Mo', 'Klatschmohn'], ['Gänse Blümchen', 'Gänseblümchen'], ['Löwen Zahn', 'Löwenzahn'],
];

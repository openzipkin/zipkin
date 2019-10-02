/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/* eslint-disable max-len */

//
// |-------|--------------|------------------------------------|------|
//
//          ______________            ___________________               _
//         | SERVICE NAME |          |   DURATION BAR    |              |  spanBarHeight
//          --------------            -------------------               -
//
// |-------|                                                   |------|
//   spanTreeWidthPercent                                        timelineRightMarginPercent
//         |--------------|
//           serviceNameWidthPercent
//                        |------------------------------------|
//                          timelineWidthPercent
//
export const spanTreeWidthPercent = 6; // %
export const serviceNameWidthPercent = 17; // %
export const timelineRightMarginPercent = 2; // %
export const timelineWidthPercent = 100 - (spanTreeWidthPercent + serviceNameWidthPercent + timelineRightMarginPercent);
export const spanBarHeight = 20; // px

//
// |------------------------------|---------------------------------|
//     spanTreeWidthPercent           serviceNameWidthPercent
//
//  A---------------------------------------------------------------
//  |
//  B---------------------------------------------------------------
//             |
//             C----------------------------------------------------
//             |         |
//             |         D------------------------------------------
//             |
//             E----------------------------------------------------
//
//             |----------------------------------------------------|
//                C and E's spanTreeLineWidthPercentPerDepth
//
export const spanTreeLineWidthPercentPerDepth = depth => spanTreeWidthPercent / (depth + 1) + serviceNameWidthPercent; // %

export const spanToggleButtonLengthOfSide = 16; // px
export const spanToggleButtonTranslate = 'translate(-8,-8)'; // px

//
//                                                             _     _
//                                                             |     |
//                                                             | A's spanBarOffsetY
//                                                             |     |
//            _____________________________                    |     |
// ----------|            SPAN A           |-----------------  -     |
//            -----------------------------                          |
//                                                                   | B's spanBarOffsetY
//                       ______________________________              |
// ---------------------|            SPAN B            |------       -
//                       ------------------------------
//
//                      |------------------------------|
//                           B's spanBarWidthPercent

export const spanBarOffsetY = index => index * spanBarHeight; // px
export const spanBarWidthPercent = width => timelineWidthPercent * (width / 100); // %



//
// --------|---------------|---------------|---------------|------|
//                                                                  _                     _
//           SERVICE NAME   SPAN NAME     DURATION                  | spanDataRowHeight   |
//          _______________________________________________         -                     | spanHeight
//         |                                               |        | spanBarRowHeight    |
//          -----------------------------------------------         |                     |
//                                                                  -                     -
//
// |-------|                                               |------|
//   spanTreeWidthPercent = timelineOffsetXPercent            timelineRightMarginPercent
//           |
//           serviceNamePosXPercent
//                          |
//                          spanNamePosXPercent
//                                        |
//                                        durationPosXPercent

/*
export const spanTreeWidthPercent = 6; // %
export const timelineRightMarginPercent = 2; // %
export const timelineWidthPercent = 100 - (spanTreeWidthPercent + timelineRightMarginPercent); // %
export const spanTreeLineWidthPercentPerDepth = depth => spanTreeWidthPercent / (depth + 1); // %

export const spanDataRowHeight = 30; // px
export const spanBarHeight = 10; // px
export const spanBarRowHeight = 14; // px
export const spanHeight = spanDataRowHeight + spanBarRowHeight; // px
export const spanOffsetY = index => index * spanHeight; // px
export const spanDataRowPosY = index => spanOffsetY(index) + spanDataRowHeight * 0.75; // px
export const spanBarRowPosY = index => spanOffsetY(index) + spanDataRowHeight; // px
export const spanBarLinePosY = index => spanBarRowPosY(index) + spanBarHeight / 2; // px
export const spanBarWidthPercent = width => timelineWidthPercent * (width / 100); // %
export const spanBarPosXPercent = left => spanTreeWidthPercent + timelineWidthPercent * (left / 100); // %

export const timelineOffsetXPercent = spanTreeWidthPercent; // %
export const timelineHeight = spanCounts => spanHeight * spanCounts; // px

export const serviceNamePosXPercent = timelineOffsetXPercent
  + (100 - timelineOffsetXPercent) * 0.05; // %
export const spanNamePosXPercent = timelineOffsetXPercent
  + (100 - timelineOffsetXPercent) * 0.35; // %
export const durationPosXPercent = timelineOffsetXPercent
  + (100 - timelineOffsetXPercent) * 0.7; // %

export const spanToggleButtonLengthOfSide = 16; // px
export const spanToggleButtonTranslate = 'translate(-8,-8)'; // px

export const spanDataRowLineHeight = 30; // px
export const spanBarRowLineHeight = 14; // px
export const expandButtonLengthOfSide = 16; // px
*/
